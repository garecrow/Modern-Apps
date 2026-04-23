package com.vayunmathur.games.chess.util
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import com.vayunmathur.games.chess.data.Board

object StockfishEngine {
    val inputChannel = Channel<String>(Channel.UNLIMITED)
    val outputChannel = Channel<String>(Channel.UNLIMITED)
    private var engineStarted = false

    enum class Difficulty(val depth: Int, val skill: Int) {
        BEGINNER(8, 0),
        INTERMEDIATE(8, 5),
        ADVANCED(8, 12),
        GRANDMASTER(8, 20)
    }

    var difficulty: Difficulty = Difficulty.BEGINNER

    private external fun startEngine()
    private external fun sendCommand(cmd: String)
    private external fun readOutput(): String

    suspend fun nextMove(board: Board) {
        inputChannel.send("position fen ${board.toFen()}")
        inputChannel.send("setoption name Skill Level value ${difficulty.skill}")
        inputChannel.send("go depth ${difficulty.depth}")
    }

    fun start(context: Context, evalFileName: String, evalFileSmallName: String) {
        if (engineStarted) return
        System.loadLibrary("stockfishjni")
        startEngine()
        engineStarted = true
        listenOutput()
        CoroutineScope(Dispatchers.IO).launch {
            sendInput()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val evalFilePath = context.getExternalFilesDir(null)!!.resolve(evalFileName).absolutePath
            val evalFileSmallPath = context.getExternalFilesDir(null)!!.resolve(evalFileSmallName).absolutePath
            Log.d("StockfishEngine", "NNUE file path: $evalFilePath")
            inputChannel.send("uci")
            inputChannel.send("setoption name EvalFile value $evalFilePath")
            inputChannel.send("setoption name EvalFileSmall value $evalFileSmallPath")
            inputChannel.send("isready")
            println("STOCKFISH READY")
        }
    }

    private fun sendInput() = CoroutineScope(Dispatchers.IO).launch {
        CoroutineScope(Dispatchers.IO).launch {
            for (cmd in inputChannel) {
                Log.d("StockfishEngine", "Stockfish Input: $cmd")
                sendCommand(cmd)
            }
        }
    }

    private fun listenOutput() = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val output = readOutput()
            if (output.isNotBlank()) {
                Log.d("StockfishEngine", "Stockfish output: $output")
                outputChannel.send(output)
            }
        }
    }

}