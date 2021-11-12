package com.github.skyeskie.mindcode.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

class Watch : CliktCommand() {
    private val watchDir by argument(help = "Directory to watch for changes")
        .file(mustExist = true, canBeFile = false)

    private val recursive: Boolean by option(
        "-r", "--r",
        help = "Watch subdirectories"
    ).flag("--no-recursive", "-R", "--single-dir", default = true)

    private val deleteCompiled: Boolean by option(
        "-d",
        help = "Delete compiled file if source file is deleted (default false)"
    ).flag("-D", "--no-delete-compiled", default = false)

    private val extension by option(
        "--extension", "-e",
        help = "Extension of Mindcode source files"
    ).default("mindcode")

    private val write by option(
        "-w", "-f",
        help = "Write output to file",
    ).flag("--no-write", "-W", default = true)

    private val clipboard by option(
        "-c", "--clip",
        help = "Copy output to clipboard",
    ).flag("--no-clip", "-C", default = false)

    private val clip = Toolkit.getDefaultToolkit().systemClipboard;

    override fun run() {
        runBlocking {
            val watchChannel = watchDir.asWatchChannel(
                mode = when (recursive) {
                    true -> KWatchChannel.Mode.Recursive
                    false -> KWatchChannel.Mode.SingleDirectory
                },
            )
            val fsWatcher = launch {
                while (isActive) {
                    val event = watchChannel.receive()
                    if (event.file.isDirectory) continue
                    when (event.kind) {
                        KWatchEvent.Kind.Created, KWatchEvent.Kind.Modified ->
                            if (shouldProcess(event.file)) {
                                val compiler = MindcodeCompiler(event.file)

                                if (!compiler.outputGood()) {
                                    for (error in compiler.errors) {
                                        System.err.println(error);
                                    }
                                    continue;
                                }

                                if (write) {
                                    val outFile = generateOutfile(event.file)
                                    outFile.writeText(compiler.mcode!!)
                                }

                                if (clipboard) {
                                    clip.setContents(StringSelection(compiler.mcode), null)
                                }

                            }
                        KWatchEvent.Kind.Deleted -> if (shouldProcess(event.file)) {
                            if (deleteCompiled) {
                                val outFile = generateOutfile(event.file)
                                if (outFile.exists()) outFile.delete()
                            }
                        }
                        else -> continue
                    }
                }
            }
            val consoleWatcher = launch(Dispatchers.IO) {
                do {
                    val ch = System.console().reader().read();
                } while (ch != -1 && ch.toChar() != 'q')
            }
            consoleWatcher.join()
            //Now close the others
            watchChannel.close()
            fsWatcher.cancel()
        }
    }

    private fun shouldProcess(file: File): Boolean {
        return file.name.endsWith(extension, ignoreCase = true)
    }

}
