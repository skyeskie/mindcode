package info.teksol.mindcode.cli

import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import info.teksol.mindcode.ast.AstNodeBuilder
import info.teksol.mindcode.ast.Seq
import info.teksol.mindcode.grammar.MindcodeLexer
import info.teksol.mindcode.grammar.MindcodeParser
import info.teksol.mindcode.mindustry.LogicInstruction
import info.teksol.mindcode.mindustry.LogicInstructionGenerator
import info.teksol.mindcode.mindustry.LogicInstructionLabelResolver
import info.teksol.mindcode.mindustry.LogicInstructionPrinter
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import java.io.File

class Compile() : CliktCommand() {
    private val file by argument(help = "Mindcode file").file(mustExist = true, canBeDir = false);

    private val output by option("-o", "--o", "--output", help = "Output .mcode file").file(
        canBeDir = false,
        mustBeWritable = true,
    );

    private val promptOverwrite by option(help = "Prompt before overwrite existing")
        .flag("--no-prompt-overwrite")

    override fun run() {
        val outFile = output ?: generateOutfile()

        if (promptOverwrite && outFile.exists() && confirm("Overwrite '" + file.name + "'") == false) {
            throw UsageError(text = "Output file exists", paramName = "output")
        }

        val sourceCode = file.readText()

        val lexer = MindcodeLexer(CharStreams.fromString(sourceCode))
        val parser = MindcodeParser(BufferedTokenStream(lexer))
        val errors: MutableList<String?> = ArrayList()
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: org.antlr.v4.runtime.Recognizer<*, *>?,
                offendingSymbol: Any,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: org.antlr.v4.runtime.RecognitionException
            ) {
                errors.add("Syntax error: $offendingSymbol on line $line:$charPositionInLine: $msg")
            }
        })

        var mcode: String? = null
        try {
            val context: MindcodeParser.ProgramContext = parser.program()
            val prog: Seq = AstNodeBuilder.generate(context)
            var result: List<LogicInstruction?> = LogicInstructionGenerator.generateAndOptimize(prog)
            result = LogicInstructionLabelResolver.resolve(result)
            mcode = LogicInstructionPrinter.toString(result)
            outFile.writeText(mcode)
        } catch (e: RuntimeException) {
            errors.add(e.message)
            printErrorsAndQuit(errors, 1)
        }

        printErrorsAndQuit(errors, 2)
    }

    private fun printErrorsAndQuit(errors: List<String?>, statusCode: Int) {
        if(errors.isEmpty()) return;
        for(error in errors) {
            System.err.println(error)
        }
        throw ProgramResult(statusCode)
    }

    private fun generateOutfile(): File {
        var newFilename = file.nameWithoutExtension + ".mcode"
        if (newFilename == file.name) {
            newFilename = file.nameWithoutExtension + ".mc"
        }
        val full = file.parent + newFilename
        return File(full)
    }
}

class Mindcode : CliktCommand() {
    init {
        subcommands(
            CompletionCommand(),
            Compile(),
        )
    }

    override fun run() {

    }
}

fun main(args: Array<String>) = Mindcode().main(args);
