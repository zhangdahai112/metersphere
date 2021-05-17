package io.metersphere.service;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.GroupMapper;
import io.metersphere.base.mapper.UserGroupPermissionMapper;
import io.metersphere.base.mapper.ext.ExtGroupMapper;
import io.metersphere.base.mapper.ext.ExtUserGroupMapper;
import io.metersphere.commons.constants.UserGroupType;
import io.metersphere.commons.user.SessionUser;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.controller.request.group.EditGroupRequest;
import io.metersphere.dto.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class GroupService {


    @Resource
    private ExtUserGroupMapper extUserGroupMapper;
    @Resource
    private GroupMapper groupMapper;
    @Resource
    private ExtGroupMapper extGroupMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private UserGroupPermissionMapper userGroupPermissionMapper;

    public Pager<List<GroupDTO>> getGroupList(EditGroupRequest request) {
        SessionUser user = SessionUtils.getUser();
        List<UserGroupDTO> userGroup = extUserGroupMapper.getUserGroup(user.getId());
        List<String> groupTypeList = userGroup.stream().map(UserGroupDTO::getType).collect(Collectors.toList());
        return getGroups(groupTypeList, request);
    }

    public Group addGroup(EditGroupRequest request) {
        Group group = new Group();
        group.setId(UUID.randomUUID().toString());
        group.setName(request.getName());
        group.setCreator(SessionUtils.getUserId());
        group.setDescription(request.getDescription());
        group.setSystem(false);
        group.setType(request.getType());
        group.setCreateTime(System.currentTimeMillis());
        group.setUpdateTime(System.currentTimeMillis());
        if (BooleanUtils.isTrue(request.getGlobal())) {
            group.setScopeId("global");
        } else {
            group.setScopeId(request.getScopeId());
        }
        groupMapper.insertSelective(group);
        return group;
    }

    public void editGroup(EditGroupRequest request) {
        Group group = new Group();
        request.setScopeId(null);
        BeanUtils.copyBean(group, request);
        group.setCreateTime(System.currentTimeMillis());
        groupMapper.updateByPrimaryKeySelective(group);
    }

    public void deleteGroup(String id) {
        groupMapper.deleteByPrimaryKey(id);
        UserGroupPermissionExample example = new UserGroupPermissionExample();
        example.createCriteria().andGroupIdEqualTo(id);
        userGroupPermissionMapper.deleteByExample(example);
    }

    public GroupPermissionDTO getGroupResource(Group g) {
        GroupPermissionDTO dto = new GroupPermissionDTO();
        InputStream permission = getClass().getResourceAsStream("/permission.json");
        String type = g.getType();
        String id = g.getId();
        UserGroupPermissionExample userGroupPermissionExample = new UserGroupPermissionExample();
        userGroupPermissionExample.createCriteria().andGroupIdEqualTo(id);
        List<UserGroupPermission> userGroupPermissions = userGroupPermissionMapper.selectByExample(userGroupPermissionExample);
        List<String> permissionList = userGroupPermissions.stream().map(UserGroupPermission::getPermissionId).collect(Collectors.toList());
        if (permission == null) {
            throw new RuntimeException("读取文件失败!");
        } else {
            GroupJson group = null;
            try {
                group = JSON.parseObject(permission, GroupJson.class);
                List<GroupResource> resource = group.getResource();
                List<GroupPermission> permissions = group.getPermissions();
                getPermission(resource, permissions, type, dto, permissionList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dto;
    }

    public void editGroupPermission(EditGroupRequest request) {
        List<GroupPermission> permissions = request.getPermissions();
        if (CollectionUtils.isEmpty(permissions)) {
            return;
        }

        UserGroupPermissionExample userGroupPermissionExample = new UserGroupPermissionExample();
        userGroupPermissionExample.createCriteria().andGroupIdEqualTo(request.getUserGroupId());
        userGroupPermissionMapper.deleteByExample(userGroupPermissionExample);

        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        UserGroupPermissionMapper mapper = sqlSession.getMapper(UserGroupPermissionMapper.class);
        String groupId = request.getUserGroupId();
        permissions.forEach(permission -> {
            if (BooleanUtils.isTrue(permission.getChecked())) {
                String permissionId = permission.getId();
                String resourceId = permission.getResourceId();
                UserGroupPermission groupPermission = new UserGroupPermission();
                groupPermission.setId(UUID.randomUUID().toString());
                groupPermission.setGroupId(groupId);
                groupPermission.setPermissionId(permissionId);
                groupPermission.setModuleId(resourceId);
                mapper.insert(groupPermission);
            }
        });
        sqlSession.flushStatements();
    }







    private List<GroupResourceDTO> getResourcePermission(List<GroupResource> resource, List<GroupPermission> permissions, String type, List<String> permissionList) {
        List<GroupResourceDTO> dto = new ArrayList<>();
        List<GroupResource> resources = resource.stream().filter(g -> g.getId().startsWith(type)).collect(Collectors.toList());
        permissions.forEach(p -> {
            if (permissionList.contains(p.getId())) {
                p.setChecked(true);
            }
        });
        for (GroupResource r : resources) {
            GroupResourceDTO resourceDTO = new GroupResourceDTO();
            resourceDTO.setResource(r);
            List<GroupPermission> collect = permissions
                    .stream()
                    .filter(p -> StringUtils.equals(r.getId(), p.getResourceId()))
                    .collect(Collectors.toList());
            resourceDTO.setPermissions(collect);
            dto.add(resourceDTO);
        }
        return dto;
    }

    private Pager<List<GroupDTO>> getGroups(List<String> groupTypeList, EditGroupRequest request) {
        if (groupTypeList.contains(UserGroupType.SYSTEM)) {
            return getUserGroup(UserGroupType.SYSTEM, request);
        }

        if (groupTypeList.contains(UserGroupType.ORGANIZATION)) {
            return getUserGroup(UserGroupType.ORGANIZATION, request);
        }

        if (groupTypeList.contains(UserGroupType.WORKSPACE)) {
            return getUserGroup(UserGroupType.WORKSPACE, request);
        }

        if (groupTypeList.contains(UserGroupType.PROJECT)) {
            return getUserGroup(UserGroupType.PROJECT, request);
        }

        return new Pager<>();
    }
    
    private void getPermission(List<GroupResource> resource, List<GroupPermission> permissions, String type, GroupPermissionDTO dto, List<String> permissionList) {
        dto.setSystem(getResourcePermission(resource, permissions, "SYSTEM", permissionList));
        dto.setOrganization(getResourcePermission(resource, permissions, "ORGANIZATION", permissionList));
        dto.setWorkspace(getResourcePermission(resource, permissions, "WORKSPACE", permissionList));
        dto.setProject(getResourcePermission(resource, permissions, "PROJECT", permissionList));
        switch (type) {
            case "SYSTEM":
                dto.setSystem(getResourcePermission(resource, permissions, "SYSTEM", permissionList));
                dto.setOrganization(getResourcePermission(resource, permissions, "ORGANIZATION", permissionList));
                dto.setWorkspace(getResourcePermission(resource, permissions, "WORKSPACE", permissionList));
                dto.setProject(getResourcePermission(resource, permissions, "PROJECT", permissionList));
                break;
            case "ORGANIZATION":
                dto.setOrganization(getResourcePermission(resource, permissions, "ORGANIZATION", permissionList));
                dto.setWorkspace(getResourcePermission(resource, permissions, "WORKSPACE", permissionList));
                dto.setProject(getResourcePermission(resource, permissions, "PROJECT", permissionList));
                break;
            case "WORKSPACE":
                dto.setWorkspace(getResourcePermission(resource, permissions, "WORKSPACE", permissionList));
                dto.setProject(getResourcePermission(resource, permissions, "PROJECT", permissionList));
                break;
            case "PROJECT":
                dto.setProject(getResourcePermission(resource, permissions, "PROJECT", permissionList));
                break;
            default:
        }
    }


    private Pager<List<GroupDTO>> getUserGroup(String groupType, EditGroupRequest request) {
        List<String> types = new ArrayList<>();
        String orgId = SessionUtils.getCurrentOrganizationId();
        List<String> scopes = Arrays.asList("global", orgId);
        int goPage = request.getGoPage();
        int pageSize = request.getPageSize();
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        switch (groupType) {
            case UserGroupType.SYSTEM:
                types = Arrays.asList(UserGroupType.SYSTEM,UserGroupType.ORGANIZATION, UserGroupType.WORKSPACE, UserGroupType.PROJECT);
                scopes = new ArrayList<>();
                break;
            case UserGroupType.ORGANIZATION:
                types = Arrays.asList(UserGroupType.ORGANIZATION, UserGroupType.WORKSPACE, UserGroupType.PROJECT);
                break;
            case UserGroupType.WORKSPACE:
                types = Arrays.asList(UserGroupType.WORKSPACE, UserGroupType.PROJECT);
                break;
            case UserGroupType.PROJECT:
                types.add(UserGroupType.PROJECT);
                break;
            default:
        }
        request.setTypes(types);
        request.setScopes(scopes);
        List<GroupDTO> groups = extGroupMapper.getGroupList(request);
        return PageUtils.setPageInfo(page, groups);
    }

}
