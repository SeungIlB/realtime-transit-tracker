package com.realtimetransit.backend.provider.repository;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.provider.entity.TransitProviderEntity;

@Mapper
public interface TransitProviderMapper {

	List<TransitProviderEntity> findAll();

	List<TransitProviderEntity> findAllActive();

	Optional<TransitProviderEntity> findByCode(@Param("code") String code);

	Optional<TransitProviderEntity> findById(@Param("id") long id);
}
