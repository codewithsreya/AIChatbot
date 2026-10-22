package com.example.aichatbot

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val chatText = findViewById<TextView>(R.id.chatText)

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()

            if (message.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please type a message first",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val currentText = chatText.text.toString()

                chatText.text = if (currentText.isEmpty()) {
                    "You: $message\n\nAI: Thinking..."
                } else {
                    "$currentText\n\nYou: $message\n\nAI: Thinking..."
                }

                messageInput.text.clear()
                sendButton.isEnabled = false

                askGemini(message, chatText, sendButton)
            }
        }
    }

    private fun askGemini(
        message: String,
        chatText: TextView,
        sendButton: Button
    ) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank()) {
                runOnUiThread {
                    chatText.append("\n\nError: API key not found.")
                    sendButton.isEnabled = true
                }
                return
            }

            val json = JSONObject().apply {
                put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(
                                JSONObject().put("text", message)
                            )
                        )
                    )
                )
            }

            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        chatText.append(
                            "\n\nAI Error: ${e.message ?: "Network error"}"
                        )
                        sendButton.isEnabled = true
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val responseBody = it.body?.string().orEmpty()

                        val answer = if (it.isSuccessful) {
                            try {
                                JSONObject(responseBody)
                                    .getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text")
                            } catch (_: Exception) {
                                "Could not read AI response."
                            }
                        } else {
                            try {
                                val errorObj = JSONObject(responseBody).optJSONObject("error")
                                val errorMsg = errorObj?.optString("message") ?: responseBody
                                "API Error (${it.code}): $errorMsg"
                            } catch (_: Exception) {
                                "API Error (${it.code}): $responseBody"
                            }
                        }

                        runOnUiThread {
                            val updatedText = chatText.text.toString()
                            val thinkingText = "AI: Thinking..."

                            chatText.text = updatedText.replace(
                                thinkingText,
                                "AI: $answer"
                            )

                            sendButton.isEnabled = true
                        }
                    }
                }
            })

        } catch (e: Exception) {
            chatText.append("\n\nError: ${e.message}")
            sendButton.isEnabled = true
        }
    }
}