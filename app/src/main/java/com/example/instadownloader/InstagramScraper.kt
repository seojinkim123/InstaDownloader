package com.example.instadownloader

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URLEncoder

object
InstagramScraper {
    private const val INSTAGRAM_DOCUMENT_ID = "8845758582119845"
    private const val INSTAGRAM_URL = "https://www.instagram.com/graphql/query"
    private val client = OkHttpClient()

    fun scrapePostMedia(shortcodeOrUrl: String, quality: String = "high"): List<String> {
        // shortcode 추출
        val shortcode = if (shortcodeOrUrl.contains("http")) {
            shortcodeOrUrl.split("/p/").last().split("/")[0]
        } else {
            shortcodeOrUrl
        }

        println("인스타그램 게시물 스크래핑 중: $shortcode")

        // variables JSON
        val variablesJson = JSONObject().apply {
            put("shortcode", shortcode)
            put("fetch_tagged_user_count", JSONObject.NULL)
            put("hoisted_comment_id", JSONObject.NULL)
            put("hoisted_reply_id", JSONObject.NULL)
        }.toString()

        val variables = URLEncoder.encode(variablesJson, "UTF-8")
        val body = "variables=$variables&doc_id=$INSTAGRAM_DOCUMENT_ID"

        // 요청 생성
        val request = Request.Builder()
            .url(INSTAGRAM_URL)
            .post(RequestBody.create("application/x-www-form-urlencoded".toMediaType(), body))
            .addHeader("content-type", "application/x-www-form-urlencoded")
            .addHeader("x-csrftoken", "MXu5wPd59xPx1WWFL5jTyfxGsVFKB6Tp")
            .addHeader("x-ig-app-id", "936619743392459")
            .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .build()

        // 동기 요청 (코루틴 안에서 withContext(IO)로 감싸는게 안전)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Request failed: ${response.code}")

            val jsonStr = response.body?.string() ?: throw Exception("Empty response")
            println("Instagram API Response: $jsonStr")
            
            val rootJson = JSONObject(jsonStr)
            
            // 오류 체크
            if (rootJson.has("errors")) {
                val errors = rootJson.getJSONArray("errors")
                throw Exception("Instagram API Error: ${errors.getJSONObject(0).getString("message")}")
            }
            
            val dataJson = rootJson.optJSONObject("data")
            if (dataJson == null) {
                throw Exception("No data field in response")
            }
            
            val mediaJson = dataJson.optJSONObject("xdt_shortcode_media")
            if (mediaJson == null) {
                throw Exception("게시물을 찾을 수 없습니다. 비공개 게시물이거나 삭제된 게시물일 수 있습니다.")
            }

            return extractMediaUrls(mediaJson, quality)
        }
    }

    private fun extractMediaUrls(postData: JSONObject, quality: String): List<String> {
        val urls = mutableListOf<String>()
        val qualityIndex = mapOf("low" to 0, "medium" to 1, "high" to 2)
        val index = qualityIndex[quality] ?: 2

        val typeName = postData.getString("__typename")

        if (typeName == "XDTGraphSidecar") {
            // 여러 장 (캐러셀)
            val children = postData
                .getJSONObject("edge_sidecar_to_children")
                .getJSONArray("edges")

            for (i in 0 until children.length()) {
                val node = children.getJSONObject(i).getJSONObject("node")
                if (node.getBoolean("is_video")) {
                    urls.add(node.getString("video_url"))
                } else {
                    val displayResources = node.getJSONArray("display_resources")
                    val resource = if (displayResources.length() > index) {
                        displayResources.getJSONObject(index)
                    } else {
                        displayResources.getJSONObject(displayResources.length() - 1)
                    }
                    urls.add(resource.getString("src"))
                }
            }
        } else {
            // 단일 미디어
            if (postData.getBoolean("is_video")) {
                urls.add(postData.getString("video_url"))
            } else {
                val displayResources = postData.getJSONArray("display_resources")
                val resource = if (displayResources.length() > index) {
                    displayResources.getJSONObject(index)
                } else {
                    displayResources.getJSONObject(displayResources.length() - 1)
                }
                urls.add(resource.getString("src"))
            }
        }

        return urls
    }
}