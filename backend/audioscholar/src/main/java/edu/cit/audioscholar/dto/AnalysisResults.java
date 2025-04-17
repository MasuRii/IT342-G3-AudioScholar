package edu.cit.audioscholar.dto;

import java.util.List;
import java.util.Collections;

public class AnalysisResults {

    private final List<String> keywordsAndTopics;
    private final boolean success;
    private final String errorMessage;

    public AnalysisResults(List<String> keywordsAndTopics) {
        this.keywordsAndTopics = Collections.unmodifiableList(keywordsAndTopics != null ? keywordsAndTopics : Collections.emptyList());
        this.success = true;
        this.errorMessage = null;
    }

    public AnalysisResults(String errorMessage) {
        this.keywordsAndTopics = Collections.emptyList();
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public List<String> getKeywordsAndTopics() {
        return keywordsAndTopics;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "AnalysisResults{" +
               "keywordsAndTopics=" + keywordsAndTopics +
               ", success=" + success +
               ", errorMessage='" + errorMessage + '\'' +
               '}';
    }
}