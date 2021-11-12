package info.teksol.mindcode.cli

import java.io.File

fun generateOutfile(srcFile: File): File {
    var newFilename = srcFile.nameWithoutExtension + ".mcode"
    if (newFilename == srcFile.name) {
        newFilename = srcFile.nameWithoutExtension + ".mc"
    }
    val full = srcFile.parent + newFilename
    return File(full)
}
