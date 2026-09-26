package com.spectrace.workflow.infrastructure;

import com.spectrace.label.application.LabelVersionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcLabelWorkflowRepositoryTest {

    @Test
    void findsCreatorUserId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcLabelWorkflowRepository repository =
                new JdbcLabelWorkflowRepository(jdbcTemplate);

        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq("label_test")
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.ResultSetExtractor<?> extractor =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);

            when(rs.next()).thenReturn(true);
            when(rs.getString("created_by_user_id"))
                    .thenReturn("user_creator");

            return extractor.extractData(rs);
        });

        assertEquals(
                "user_creator",
                repository.findCreatorUserId("label_test")
                        .orElseThrow()
        );
    }

    @Test
    void returnsEmptyWhenCreatorDoesNotExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcLabelWorkflowRepository repository =
                new JdbcLabelWorkflowRepository(jdbcTemplate);

        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq("missing")
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.ResultSetExtractor<?> extractor =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(false);

            return extractor.extractData(rs);
        });

        assertTrue(
                repository.findCreatorUserId("missing").isEmpty()
        );
    }


    @Test
    void findsCurrentWorkflowVersion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcLabelWorkflowRepository repository =
                new JdbcLabelWorkflowRepository(jdbcTemplate);

        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq("label_current")
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.ResultSetExtractor<?> extractor =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);

            when(rs.next()).thenReturn(true);
            when(rs.getString("label_version_id"))
                    .thenReturn("label_current");
            when(rs.getString("lifecycle_status"))
                    .thenReturn("DRAFT");
            when(rs.getBoolean("is_current"))
                    .thenReturn(true);

            return extractor.extractData(rs);
        });

        var version = repository.findVersion("label_current")
                .orElseThrow();

        assertEquals(
                "label_current",
                version.labelVersionId()
        );
        assertEquals(
                "DRAFT",
                version.lifecycleStatus()
        );
        assertTrue(version.current());
    }

    @Test
    void returnsEmptyWhenWorkflowVersionDoesNotExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcLabelWorkflowRepository repository =
                new JdbcLabelWorkflowRepository(jdbcTemplate);

        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq("missing")
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.ResultSetExtractor<?> extractor =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(false);

            return extractor.extractData(rs);
        });

        assertFalse(
                repository.findVersion("missing").isPresent()
        );
    }
}