package io.metersphere.base.mapper.ext;

import io.metersphere.base.domain.Group;
import io.metersphere.base.domain.Role;
import io.metersphere.base.domain.User;
import io.metersphere.controller.request.member.QueryMemberRequest;
import io.metersphere.controller.request.organization.QueryOrgMemberRequest;
import io.metersphere.dto.UserGroupDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtUserGroupMapper {

    List<UserGroupDTO> getUserGroup(@Param("userId") String userId);

    List<Group> getOrganizationMemberGroups(@Param("orgId") String orgId, @Param("userId") String userId);

    List<User> getOrgMemberList(@Param("orgMember") QueryOrgMemberRequest request);

    List<Group> getWorkspaceMemberGroups(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    List<User> getMemberList(@Param("member") QueryMemberRequest request);
}
