package com.github.skyeskie.mindcode.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

class Compile() : CliktCommand() {
    private val file by argument(help = "Mindcode file").file(mustExist = true, canBeDir = false);

    private val output by option("-o", "--o", "--output", help = "Output .mcode file").file(
        canBeDir = false,
        mustBeWritable = true,
    );

    private val promptOverwrite by option(help = "Prompt before overwrite existing")
        .flag("--no-prompt-overwrite")

    override fun run() {
        val outFile = output ?: generateOutfile(file)

        if (promptOverwrite && outFile.exists() && confirm("Overwrite '" + file.name + "'") == false) {
            throw UsageError(text = "Output file exists", paramName = "output")
        }

        val compiler = MindcodeCompiler(file)

        if (compiler.outputGood()) {
            outFile.writeText(compiler.mcode!!)
        } else {
            for (error in compiler.errors) {
                System.err.println(error)
            }
            if (compiler.mcode == null) {
                System.err.println("No compiled output")
            }
            throw ProgramResult(1)
        }
    }
}
