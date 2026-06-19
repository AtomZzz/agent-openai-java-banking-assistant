package com.microsoft.openai.samples.assistant.business.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountType(
        @JsonProperty("type") String type,
        @JsonProperty("description") String description,
        @JsonProperty("features") List<String> features,
        @JsonProperty("limits") AccountLimits limits
) {
    // 嵌套类：账户限制
    public record AccountLimits(
            @JsonProperty("minimumBalance") Double minimumBalance,
            @JsonProperty("maxWithdrawalsPerMonth") Integer maxWithdrawalsPerMonth,
            @JsonProperty("monthlyFee") Double monthlyFee,
            @JsonProperty("interestRate") Double interestRate
    ) {
    }
}