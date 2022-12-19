package com.database.sonar.impl;

import cn.edu.fudan.issue.core.process.RawIssueMatcher;
import cn.edu.fudan.issue.entity.dbo.Location;
import cn.edu.fudan.issue.entity.dbo.RawIssue;
import cn.edu.fudan.issue.util.AnalyzerUtil;
import cn.edu.fudan.issue.util.AstParserUtil;
import com.database.common.IssueInstanceStatus;
import com.database.object.*;
import com.database.service.*;
import com.database.service.impl.*;
import com.database.sonar.SonarService;
import com.database.utils.EnumUtil;
import com.database.utils.GitUtil;
import com.database.utils.IssueUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SonarServiceImpl implements SonarService {

    private RepositoryService repositoryService = new RepositoryServiceImpl();
    private BranchService branchService = new BranchServiceImpl();
    private CommitService commitService = new CommitServiceImpl();
    private IssueCaseService caseService = new IssueCaseServiceImpl();
    private IssueInstanceService instanceService = new IssueInstanceServiceImpl();
    private IssueLocationService locationService = new IssueLocationServiceImpl();

    @Override
    public void showInstanceByCommit(int commit) {
        List<IssueInstance> instances = instanceService.getInstByCommit(commit);
        instances.forEach(issueInstance -> System.out.println(issueInstance));
    }

    @Override
    public void showCaseByCommiter(String commiter) {

    }

    @Override
    public void showCaseByTime(Date time) {
        List<IssueCase> cases = caseService.getCaseByTime(time);
        // 指定时间后引入的
        List<IssueCase> appear = new ArrayList<>();
        // 指定时间后解决的
        List<IssueCase> solve = new ArrayList<>();
        cases.forEach(issueCase -> {
            // appear time > time
            // TODO：处理引入和解决时间的问题
//            if (issueCase.getAppearTime().after(time)) appear.add(issueCase);
//            // solve time > time
//            if (issueCase.getSolveTime().after(time)) solve.add(issueCase);
        });
        // 输出引入的
        System.out.println("指定时间后引入的：");
        appear.forEach(issueCase -> System.out.println(issueCase));
        // 输出解决的
        System.out.println("指定时间后解决的：");
        solve.forEach(issueCase -> System.out.println(issueCase));
    }

    @Override
    public boolean importRepository(String baseDir, String pjName) {
        String pathName = baseDir + File.separator + pjName;                // 获取仓库路径
        // 1、确定本地项目（即确定repo）
        Git git = null;
        try {
            git = Git.open(new File(pathName));
            git.checkout().setCreateBranch(false).setName("main").call();   // 切换到主分支
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 2、完成repo，branch，commit的入库
        Repository repository = new Repository(baseDir, pjName);
        int repositoryId = repositoryService.insert(repository);
        // 获取所有的分支列表
        try {
            List<Ref> refs = git.branchList().call();
            // 遍历所有的branch
            for (Ref ref : refs) {
                // 获取该分支的基本信息
                String branchName = ref.getName().substring(GitUtil.GitBranchType.LOCAL.getPrefix().length());
                Branch branch = new Branch(branchName, repositoryId);
                // 将该分支信息入库
                int branchId = branchService.insert(branch);
                // 切换到该分支
                git.checkout().setCreateBranch(false).setName(branchName).call();
                // 从日志中获得commit信息
                List<RevCommit> commits = new ArrayList<>();
                for (RevCommit commit : git.log().call()) {
                    commits.add(commit);
                }
                // 遍历commit，进行入库与扫描（注！这里只完成扫描，不完成扫描信息的入库）
                for (int i = commits.size() - 1; i > 0; i--) {
                    RevCommit commit = commits.get(i);
                    // new一个新的commit并将其入库
                    Commit commitInsert = new Commit(commit.getName(), commit.getAuthorIdent().getWhen(), commit.getAuthorIdent().getName(), branchId);
                    int commitId = commitService.insert(commitInsert);

                    // 3、对当前入库的commit代码进行扫描
                    String commitHash = commitInsert.getCommitHash();
                    // TODO: 切换扫描的版本commit

                    IssueUtil.scannerCommit(commitHash, repositoryId, branchId, commitId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4、仓库，分支，版本的元信息处理完成，下面完成issue的入库（包括instance、case和location）
        try {
            importIssue(repositoryId, pathName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void importIssue(int repositoryId, String pathName) throws Exception {
        // 前面已经将仓库的元信息入库，该函数将issue case、instance与location入库
        // 确定仓库分支的数目
        int branchNum = branchService.getNumByRepoId(repositoryId);
        // 遍历分支，逐分支逐commit入库issue
        // TODO: 这里为什么可以直接branch从0自增？？
        for (int branchId = 0; branchId < branchNum; branchId++) {
            // 获取该分支下的所有commit id（这里是由我们维护的id）
            int[] commitIdList = commitService.getIdByBranchId(branchId);
            // 先读出该分支下第一个commit的issue，入库instance和case
            List<RawIssue> preRawIssues = IssueUtil.getSonarResult(repositoryId, branchId, commitIdList[0]);
            AnalyzerUtil.addExtraAttributeInRawIssues(preRawIssues, pathName);
            List<IssueInstance> preIssueInstances = new ArrayList<>();

            // 通过得到的 raw issue，封装case和instance
            for (RawIssue preRawIssue : preRawIssues) {
                // 封装成case并入库
                IssueCase issueCase = new IssueCase(EnumUtil.RawIssueType2IssueCaseType(preRawIssue.getType()), commitIdList[0]);
                int caseId = caseService.insert(issueCase);

                // 封装成instance并入库
                IssueInstance issueInstance = new IssueInstance(commitIdList[0], caseId, IssueInstanceStatus.APPEAR, preRawIssue.getFileName());
                int instanceId = instanceService.insert(issueInstance);
                issueInstance.setIssueInstanceId(instanceId);

                // 加入pre列
                preIssueInstances.add(issueInstance);

                // 封装location并入库
                for (int j = 0; j < preRawIssue.getLocations().size(); j++) {
                    Location rawLocation = preRawIssue.getLocations().get(j);
                    IssueLocation issueLocation = new IssueLocation(instanceId, j, rawLocation.getStartLine(), rawLocation.getEndLine());
                    locationService.insert(issueLocation);
                }
            }

            // 开始处理该分支下后续的版本号
            for (int i = 1; i < commitIdList.length; i++) {
                int commitId = commitIdList[i];

                // 获取该commit下的raw issue
                List<RawIssue> curRawIssues = IssueUtil.getSonarResult(repositoryId, branchId, commitId);
                AnalyzerUtil.addExtraAttributeInRawIssues(curRawIssues, pathName);
                List<IssueInstance> curIssueInstances = new ArrayList<>();

                // 进行与pre的匹配，若匹配上则不新建case
                RawIssueMatcher.match(preRawIssues, curRawIssues, addAllMethodsAndFields(pathName));

                // 遍历并入库所有的instance，部分case
                for (RawIssue curRawIssue : curRawIssues) {
                    // 没有匹配到前面的instance
                    if (curRawIssue.getMappedRawIssue() == null) {
                        // 封装case并入库
                        IssueCase issueCase = new IssueCase(EnumUtil.RawIssueType2IssueCaseType(curRawIssue.getType()), commitId);
                        int caseId = caseService.insert(issueCase);

                        // 封装instance并入库
                        IssueInstance issueInstance = new IssueInstance(commitId, caseId, IssueInstanceStatus.APPEAR, curRawIssue.getFileName());
                        int instanceId = instanceService.insert(issueInstance);
                        issueInstance.setIssueInstanceId(instanceId);
                        curIssueInstances.add(issueInstance);

                        // 封装location并入库
                        for (int j = 0; j < curRawIssue.getLocations().size(); j++) {
                            Location rawLocation = curRawIssue.getLocations().get(j);
                            IssueLocation issueLocation = new IssueLocation(instanceId, j, rawLocation.getStartLine(), rawLocation.getEndLine());
                            locationService.insert(issueLocation);
                        }
                    }
                    else {
                        // 封装instance，并匹配到case
                        // TODO: get issue case id by match
                        IssueInstance issueInstance = new IssueInstance(commitId, 0, IssueInstanceStatus.APPEAR, curRawIssue.getFileName());
                        int instanceId = instanceService.insert(issueInstance);
                        issueInstance.setIssueInstanceId(instanceId);
                        curIssueInstances.add(issueInstance);

                        // 入库location
                        for (int j = 0; j < curRawIssue.getLocations().size(); j++) {
                            Location rawLocation = curRawIssue.getLocations().get(j);
                            IssueLocation issueLocation = new IssueLocation(instanceId, j, rawLocation.getStartLine(), rawLocation.getEndLine());
                            locationService.insert(issueLocation);
                        }
                    }
                }

                // 可能存在当前版本不存在，上个版本存在的问题，是在该版本被解决
                for (int j = 0; j < preRawIssues.size(); j++) {
                    if (preRawIssues.get(j).getMappedRawIssue() == null) {
                        // 该问题已经被解决
                        int caseId = preIssueInstances.get(j).getIssueCaseId();
                        IssueCase issueCase = caseService.getCaseById(caseId);
                        issueCase.setSolveCommitId(preIssueInstances.get(j).getCommitId());
                        caseService.update(issueCase);
                    }
                }

                // issue已经入库完成，更新循环控制变量
                for (RawIssue curRawIssue : curRawIssues) {
                    curRawIssue.resetMappedInfo();
                }
                preIssueInstances = curIssueInstances;
                preRawIssues = curRawIssues;
            }
        }
    }

    //递归遍历工程文件夹
    public Set<String> addAllMethodsAndFields(String pathName) throws IOException {
        File file = new File(pathName);
        File[] files = file.listFiles();
        Set<String> res = new HashSet<>();

        for(File file1 : files){
            if(file1.isDirectory()){
                Set<String> out = addAllMethodsAndFields(file1.getAbsolutePath());
                res.addAll(out);
            }
            else {
                res = AstParserUtil.getMethodsAndFieldsInFile(file1.getAbsolutePath());
            }
        }
        return res;
    }

}

