package com.cyberraksha.guardian;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface GeminiService {
    @POST("chat/completions")
    Call<GeminiResponse> checkAppSecurity(
            @Header("Authorization") String authHeader,
            @Body GeminiRequest request
    );

    @POST("chat/completions")
    Call<GeminiResponse> analyzeImage(
            @Header("Authorization") String authHeader,
            @Body MultiModalGeminiRequest request
    );
}

class GeminiRequest {
    public String model;
    public List<Message> messages;
    public Integer max_tokens;

    public GeminiRequest(String prompt) {
        this.model = "google/gemini-flash-1.5";
        this.messages = new ArrayList<>();
        this.messages.add(new Message("user", prompt));
        this.max_tokens = 800;
    }
    
    public void setModel(String model) {
        this.model = model;
    }

    public static class Message {
        public String role;
        public String content;
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}

class MultiModalGeminiRequest {
    public String model;
    public List<Message> messages;
    public Integer max_tokens;

    public MultiModalGeminiRequest(String prompt, String base64Image) {
        // Model identifier for Gemini Flash 1.5 on OpenRouter (supports text + vision)
        this.model = "google/gemini-flash-1.5";
        this.messages = new ArrayList<>();
        
        // Using Maps for content parts to ensure only non-null fields are serialized
        List<Map<String, Object>> content = new ArrayList<>();
        
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);
        
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);
        
        this.messages.add(new Message("user", content));
        this.max_tokens = 512;
    }

    public static class Message {
        public String role;
        public Object content;
        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }
    }
}

class GeminiResponse {
    public List<Choice> choices;

    public static class Choice {
        public Message message;
    }

    public static class Message {
        public String content;
    }

    public String getResponseText() {
        if (choices != null && !choices.isEmpty() &&
            choices.get(0).message != null &&
            choices.get(0).message.content != null) {
            return choices.get(0).message.content;
        }
        return "";
    }
}
