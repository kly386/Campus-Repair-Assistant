package com.example.campusrepair.util;

/** 余弦相似度（RAG 向量检索的核心度量） */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * 计算两个向量的余弦相似度，范围 [-1, 1]，越接近 1 语义越相近。
     * 任一向量长度不匹配或范数为 0 时返回 0（视为完全不相关）。
     */
    public static double of(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
