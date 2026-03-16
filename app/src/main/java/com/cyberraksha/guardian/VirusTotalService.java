package com.cyberraksha.guardian;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface VirusTotalService {
    @GET("files/{id}")
    Call<VirusTotalResponse> getFileReport(
            @Header("x-apikey") String apiKey,
            @Path("id") String fileHash
    );
}

class VirusTotalResponse {
    Data data;

    static class Data {
        Attributes attributes;
    }

    static class Attributes {
        LastAnalysisStats last_analysis_stats;
    }

    static class LastAnalysisStats {
        int malicious;
        int suspicious;
        int undetected;
        int harmless;
    }
}
