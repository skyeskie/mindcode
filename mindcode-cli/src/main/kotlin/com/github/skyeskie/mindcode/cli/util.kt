package com.github.skyeskie.mindcode.cli

import java.io.File

fun generateOutfile(srcFile: File): File {
    var newFilename = srcFile.nameWithoutExtension + ".mcode"
    if (newFilename == srcFile.name) {
        newFilename = srcFile.nameWithoutExtension + ".mc"
    }
    return srcFile.parentFile.resolve(newFilename)
}
