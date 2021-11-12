package info.teksol.mindcode.cli

import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands


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
