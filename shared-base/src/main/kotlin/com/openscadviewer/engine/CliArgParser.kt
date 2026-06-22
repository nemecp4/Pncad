package com.openscadviewer.engine

object CliArgParser {
    data class CliArgs(
        val inputPath: String,
        val outputPath: String,
        val timeoutSeconds: Int = 60
    )

    fun parse(args: Array<String>): Result<CliArgs> {
        val positional = mutableListOf<String>()
        var timeoutSeconds = 60
        var i = 0

        while (i < args.size) {
            when {
                args[i] == "--timeout" -> {
                    i++
                    if (i >= args.size) {
                        return Result.failure(
                            IllegalArgumentException("--timeout requires a value")
                        )
                    }
                    val value = args[i].toIntOrNull()
                        ?: return Result.failure(
                            IllegalArgumentException("--timeout value must be an integer, got: ${args[i]}")
                        )
                    if (value !in 1..3600) {
                        return Result.failure(
                            IllegalArgumentException("--timeout must be between 1 and 3600, got: $value")
                        )
                    }
                    timeoutSeconds = value
                }
                else -> positional.add(args[i])
            }
            i++
        }

        if (positional.size < 2) {
            return Result.failure(
                IllegalArgumentException(
                    "Usage: <input.scad> <output.stl> [--timeout <seconds>]"
                )
            )
        }

        return Result.success(
            CliArgs(
                inputPath = positional[0],
                outputPath = positional[1],
                timeoutSeconds = timeoutSeconds
            )
        )
    }
}
