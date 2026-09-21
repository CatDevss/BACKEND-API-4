package com.visiona.georuraldatahub.repository;

import com.visiona.georuraldatahub.model.DataSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSetRepository extends JpaRepository<DataSet, Long> {

    List<DataSet> findBySource_Id(Long sourceId);
}
