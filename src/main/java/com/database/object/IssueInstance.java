package com.database.object;

import com.database.common.IssueInstanceStatus;

public class IssueInstance {
    private int issueInstanceId;

    private int commitId;

    private int issueCaseId;

    private IssueInstanceStatus status;

    private String fileName;

    public IssueInstance(int commitId, int issueCaseId, IssueInstanceStatus status, String fileName) {
        this.commitId = commitId;
        this.issueCaseId = issueCaseId;
        this.status = status;
        this.fileName = fileName;
    }

    public int getIssueInstanceId() {
        return issueInstanceId;
    }

    public void setIssueInstanceId(int issueInstanceId) {
        this.issueInstanceId = issueInstanceId;
    }

    public int getCommitId() {
        return commitId;
    }

    public void setCommitId(int commitId) {
        this.commitId = commitId;
    }

    public int getIssueCaseId() {
        return issueCaseId;
    }

    public void setIssueCaseId(int issueCaseId) {
        this.issueCaseId = issueCaseId;
    }

    public IssueInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(IssueInstanceStatus status) {
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
