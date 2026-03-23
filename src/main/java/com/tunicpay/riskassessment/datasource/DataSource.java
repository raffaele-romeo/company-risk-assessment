package com.tunicpay.riskassessment.datasource;

public interface DataSource<T> {

    String name();

    DataSourceResult<T> fetch(String companyNumber, String companyName, String jurisdiction);
}
