package com.portal.auth.application.port.in;

public interface IssuePasswordUseCase {
    String issueTemporaryPassword(String email);
}
