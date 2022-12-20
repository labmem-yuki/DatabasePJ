package com.test;

import com.database.dao.CommitDao;
import com.database.dao.impl.CommitDaoImpl;
import com.database.object.Commit;
import com.database.service.CommitService;
import com.database.service.impl.CommitServiceImpl;
import com.database.utils.JDBCUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;

public class CommitTest {

    private CommitDao commitDao = new CommitDaoImpl();
    private CommitService commitService = new CommitServiceImpl();

    @Test
    public void testInsert() {
        Commit commit = new Commit("hhhhhash", new Date(), "me", 1);
        commitDao.insert(commit);
    }

    @Test
    public void testQueryById() {
        int id = 1;
        Commit commit = commitDao.queryById(id);
        System.out.println(commit);
    }

    @Test
    public void testQueryByHashAndBranchId() {
        String hash = "hhhhhash";
        int branch_id = 1;
        Commit commit = commitDao.queryByHashAndBranchId(hash, branch_id);
        System.out.println(commit);
    }

    @Test
    public void testServiceInsert() {
        Commit commit = new Commit("hhhhhhhhhhhhash", new Date(), "me", 1);
        int id = commitService.insert(commit);
        System.out.println(id);
    }

    @Test
    public void testTimeStamp() {
        String sql = "select commit_id commitId, branch_id branchId, commiter, commit_time commitTime, commit_hash commitHash from commit where commit_time > ?";
        ArrayList<Commit> query = JDBCUtil.query(Commit.class, sql, new Date());
        query.forEach(commit -> System.out.println(commit));
    }
}
