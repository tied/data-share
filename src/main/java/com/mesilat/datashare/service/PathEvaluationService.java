package com.mesilat.datashare.service;

public interface PathEvaluationService {
    Object evaluate(String xpath);
    Object evaluate(Long pageId, String xpath);
    Object evaluate(String label, String xpath);
}