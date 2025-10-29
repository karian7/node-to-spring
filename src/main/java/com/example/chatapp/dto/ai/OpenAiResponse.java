package com.example.chatapp.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices
) {
    public record Choice(
        int index,
        Delta delta,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Delta(String content) {
    }
}
