package com.github.mmauro.mkvtoolnix_wrapper

import com.github.mmauro.mkvtoolnix_wrapper.MkvToolnixCommandResult.*
import com.github.mmauro.mkvtoolnix_wrapper.MkvToolnixCommandResult.Line.Type
import com.github.mmauro.mkvtoolnix_wrapper.utils.CachedSequence
import java.io.BufferedReader

/**
 * Abstract class that represents a result given by a MKV Toolnix binary.
 * There are two "versions" of this class:
 * * [Lazy]: its output can be iterated while the program is executing
 * * [Sync]: all output is parsed beforehand
 *
 * Both implementations contain the following information:
 * * `output`: a sequence of [Line]s that contain the output given by the execution
 * * `exitCode`: the numerical exit code of the process
 * * `success`: true if the exit code is `0` and the output contains no error or warnings
 */
sealed class MkvToolnixCommandResult<COMMAND : MkvToolnixCommand<*>>(val command: COMMAND) {

    /**
     * A line outputted by the program execution
     * The type can be [Type.ERROR], [Type.WARNING] or, for all other lines, [Type.INFO]
     */
    data class Line(
        val message: String,
        val type: Type
    ) {
        enum class Type {
            INFO, WARNING, ERROR
        }

        override fun toString(): String {
            return "${type.name}: $message";
        }
    }

    /** The exit code of the process. `0` means everything executed successfully */
    abstract val exitCode: Int
    /**
     * [Sequence] of [Line]s. In the [Lazy] implementation it can be iterated while the program is still executing.
     * It can be iterated multiple times.
     */
    abstract val output: Sequence<MkvToolnixCommandResult.Line>

    /**
     * true if the exit code is `0` and the output contains no error or warnings
     *
     * WARNING: Evaluating this variable in the [Lazy] implementation causes the method to halt until the process finishes
     */
    val success by lazy {
        exitCode == 1 && !(output.hasErrors() || output.hasWarnings())
    }

    private fun outputLines(printCommand: Boolean, printOutput: Boolean, printExitCode: Boolean): Sequence<String> {
        return sequence {
            if (printCommand) {
                yield(command.toString())
                yield("")
            }
            if (printOutput) {
                yieldAll(output.map { it.toString() })
                yield("")
            }
            if (printExitCode) {
                yield("Exit code: $exitCode")
            }
        }.asSequence()
    }

    /**
     * @param printCommand whether to include the command used at the start of the string. Default: `false`
     * @param printOutput whether to include the output of the program. Default: `true`
     * @param printExitCode whether to include the exit code of the program. Default: `true`
     * @return a string representation of this program execution
     */
    fun toString(printCommand: Boolean = false, printOutput: Boolean = true, printExitCode: Boolean = true) = StringBuilder().apply {
        outputLines(printCommand, printOutput, printExitCode).forEach {
            appendln(it)
        }
    }.toString()

    /**
     * Prints a string representation of this program.
     *
     * Please notice that this method, if called in an [Lazy] implementation, prints each output line as soon as it's generated by the process.
     *
     * @param printCommand whether to include the command used at the start of the string. Default: `false`
     * @param printOutput whether to include the output of the program. Default: `true`
     * @param printExitCode whether to include the exit code of the program. Default: `true`
     */
    fun print(printCommand: Boolean = false, printOutput: Boolean = true, printExitCode: Boolean = true) {
        outputLines(printCommand, printOutput, printExitCode).forEach {
            println(it)
        }
    }

    /**
     * @return a string representation of this program execution with:
     * * command: no
     * * output: yes
     * * exitCode: yes
     * @see toString
     */
    override fun toString(): String {
        return toString(false)
    }

    /**
     * Lazy implementation.
     * See [MkvToolnixCommandResult] for details
     */
    class Lazy<COMMAND : MkvToolnixCommand<COMMAND>> internal constructor(
        command: COMMAND,
        private val reader: BufferedReader,
        exitCodeEvaluator: () -> Int,
        opt: CachedSequence<MkvToolnixCommandResult.Line>
    ) : MkvToolnixCommandResult<COMMAND>(command), AutoCloseable {

        override val output: Sequence<Line> = opt
        override val exitCode by lazy(exitCodeEvaluator)

        internal fun waitForCompletion(exceptionInitializer: ExceptionInitializer<COMMAND>): Sync<COMMAND> {
            if (!success) {
                throw exceptionInitializer(this, "Errors/warnings have been produced", null)
            }
            return toSync()
        }

        /**
         * Returns a sync version of this object. Takes care of closing the input stream. Halts until program execution terminates.
         */
        fun toSync() = use {
            Sync(command, exitCode, output.toList())
        }

        override fun close() {
            reader.close()
        }
    }

    /**
     * Sync implementation.
     * See [MkvToolnixCommandResult] for details
     *
     * @param outputList the list of lines as a list. Contains the same elements present in the [output] sequence
     */
    class Sync<COMMAND : MkvToolnixCommand<*>> internal constructor(
        command: COMMAND,
        override val exitCode: Int,
        val outputList: List<MkvToolnixCommandResult.Line>
    ) : MkvToolnixCommandResult<COMMAND>(command) {
        override val output = outputList.asSequence()
    }
}

fun Sequence<MkvToolnixCommandResult.Line>.hasWarnings() = any { it.type == MkvToolnixCommandResult.Line.Type.WARNING }
fun Sequence<MkvToolnixCommandResult.Line>.hasErrors() = any { it.type == MkvToolnixCommandResult.Line.Type.ERROR }
fun Sequence<MkvToolnixCommandResult.Line>.hasInfo() = any { it.type == MkvToolnixCommandResult.Line.Type.INFO }

fun Sequence<MkvToolnixCommandResult.Line>.warnings() = filter { it.type == MkvToolnixCommandResult.Line.Type.WARNING }
fun Sequence<MkvToolnixCommandResult.Line>.errors() = filter { it.type == MkvToolnixCommandResult.Line.Type.ERROR }
fun Sequence<MkvToolnixCommandResult.Line>.info() = filter { it.type == MkvToolnixCommandResult.Line.Type.INFO }

