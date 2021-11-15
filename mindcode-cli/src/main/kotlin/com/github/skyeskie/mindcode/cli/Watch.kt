package com.github.skyeskie.mindcode.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.*
import org.apache.logging.log4j.kotlin.Logging
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

class Watch : CliktCommand(), Logging {
    private val watchDir by argument(help = "Directory to watch for changes")
        .file(mustExist = true, canBeFile = false)

    private val recursive: Boolean by option(
        "-r",
        help = "Watch subdirectories"
    ).flag("--no-recursive", "-R", "--single-dir", default = true)

    private val deleteCompiled: Boolean by option(
        "-d", "--delete",
        help = "Delete compiled file if source file is deleted (default false)"
    ).flag("-D", "--no-delete-compiled", default = false)

    private val extension by option(
        "-e",
        help = "Extension of Mindcode source files"
    ).default("mindcode")

    private val write: Boolean by option(
        "-w", "-f",
        help = "Write output to file",
    ).flag("--no-write", "-W", default = true)

    private val clipboard: Boolean by option(
        "-c", "--clip",
        help = "Copy output to clipboard",
    ).flag("--no-clip", "-C", default = false)

    private val clip = Toolkit.getDefaultToolkit().systemClipboard

    override fun run() {
        logger.info("Starting watch command")
        logger.debug(this)
        runBlocking {
            logger.info("Launching coroutines")
            val scope = CoroutineScope(coroutineContext)
            val watchChannel = watchDir.asWatchChannel(
                mode = when (recursive) {
                    true -> KWatchChannel.Mode.Recursive
                    false -> KWatchChannel.Mode.SingleDirectory
                },
                scope = scope
            )
            val fsWatcher = launch {
                logger.info("Started FS watcher")
                while (isActive) {
                    logger.info("Waiting for change")
                    val event = watchChannel.receive()
                    if (event.file.isDirectory) continue
                    logger.info("Handling ${event.kind}")
                    logger.debug(event)
                    when (event.kind) {
                        KWatchEvent.Kind.Created, KWatchEvent.Kind.Modified -> {
                            logger.debug("In create/modify")
                            if (shouldProcess(event.file)) {
                                logger.trace("Should process")
                                val compiler = MindcodeCompiler(event.file)

                                if (!compiler.outputGood()) {
                                    for (error in compiler.errors) {
                                        System.err.println(error)
                                    }
                                    continue
                                }

                                logger.debug("Entering output portion")
                                if (write) {
                                    val outFile = generateOutfile(event.file)
                                    logger.info("Writing to $outFile")
                                    outFile.writeText(compiler.mcode!!)
                                }

                                if (clipboard) {
                                    logger.info("Setting to clipboard")
                                    clip.setContents(StringSelection(compiler.mcode), null)
                                }

                            }
                            logger.debug("Finish create/modify")
                        }
                        KWatchEvent.Kind.Deleted -> {
                            logger.debug("In deleted")
                            if (deleteCompiled) {
                                if (shouldProcess(event.file)) {
                                    val outFile = generateOutfile(event.file)
                                    logger.info("Deleting $outFile")
                                    if (outFile.exists()) outFile.delete()
                                }
                            }
                        }
                        else -> continue
                    }
                }
            }
            val consoleWatcher = launch(Dispatchers.IO) {
                logger.debug("Starting console watch")
                do {
                    val ch = System.console().reader().read()
                    logger.trace("Read character: '${ch.toChar()}'")
                } while (ch != -1 && ch.toChar() != 'q')
                logger.info("Quit character found")
            }
            logger.info("Waiting for quit character")
            consoleWatcher.join()
            //Now close the others
            logger.info("Shut down")
            watchChannel.close()
            fsWatcher.cancel()
        }
        logger.debug("Exited blocking")
    }

    private fun shouldProcess(file: File): Boolean {
        return file.name.endsWith(extension, ignoreCase = true)
    }

}
