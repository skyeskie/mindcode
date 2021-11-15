package com.github.skyeskie.mindcode.cli

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
import org.apache.logging.log4j.kotlin.Logging
import java.io.File

class MindcodeCompiler(sourceCode: String) : Logging {

    constructor(srcFile: File) : this(srcFile.readText())

    var errors = ArrayList<String?>()

    var mcode: String? = null

    init {
        logger.trace(sourceCode)
        val lexer = MindcodeLexer(CharStreams.fromString(sourceCode))
        val parser = MindcodeParser(BufferedTokenStream(lexer))
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: org.antlr.v4.runtime.Recognizer<*, *>?,
                offendingSymbol: Any,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: org.antlr.v4.runtime.RecognitionException
            ) {
                logger.error("Syntax error: $offendingSymbol on line $line:$charPositionInLine: $msg")
                errors.add("Syntax error: $offendingSymbol on line $line:$charPositionInLine: $msg")
            }
        })
        try {
            val context: MindcodeParser.ProgramContext = parser.program()
            val prog: Seq = AstNodeBuilder.generate(context)
            logger.debug("Running compile")
            var result: List<LogicInstruction?> = LogicInstructionGenerator.generateAndOptimize(prog)
            logger.trace("Generate and optimize output:")
            logger.trace(result)
            result = LogicInstructionLabelResolver.resolve(result)
            mcode = LogicInstructionPrinter.toString(result)
            logger.trace(mcode ?: "<null mcode>")
        } catch (e: RuntimeException) {
            logger.error(e)
            errors.add(e.message)
        }
    }

    fun outputGood(): Boolean {
        return errors.isEmpty() && mcode != null
    }
}
