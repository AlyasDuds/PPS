package com.pps.profilesystem.Repository;

import com.pps.profilesystem.Entity.ArchivedOffice;
import com.pps.profilesystem.Entity.PostalOffice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchivedOfficeRepository extends JpaRepository<ArchivedOffice, Integer> {

    Optional<ArchivedOffice> findByPostalOffice(PostalOffice postalOffice);

    @Query("SELECT a FROM ArchivedOffice a " +
           "JOIN FETCH a.postalOffice po " +
           "LEFT JOIN FETCH po.area " +
           "LEFT JOIN FETCH po.cityMunicipality " +
           "WHERE po.id = :officeId")
    Optional<ArchivedOffice> findByPostalOfficeId(@Param("officeId") Integer officeId);

    boolean existsByPostalOfficeId(Integer officeId);

    @Query("SELECT ao.postalOffice.id FROM ArchivedOffice ao")
    List<Integer> findAllArchivedPostalOfficeIds();

    @Query("SELECT a FROM ArchivedOffice a " +
           "JOIN FETCH a.postalOffice po " +
           "LEFT JOIN FETCH po.area " +
           "LEFT JOIN FETCH po.cityMunicipality " +
           "ORDER BY a.archivedAt DESC")
    List<ArchivedOffice> findAllWithOffice();

    @Query("SELECT a FROM ArchivedOffice a " +
           "JOIN FETCH a.postalOffice po " +
           "LEFT JOIN FETCH po.area " +
           "LEFT JOIN FETCH po.cityMunicipality " +
           "WHERE po.area.id = :areaId " +
           "ORDER BY a.archivedAt DESC")
    List<ArchivedOffice> findAllWithOfficeByArea(@Param("areaId") Integer areaId);

    void deleteByPostalOfficeId(Integer officeId);
}