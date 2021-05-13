package io.metersphere.controller.request.group;

import io.metersphere.base.domain.Group;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EditGroupRequest extends Group {

    private int goPage;
    private int pageSize;
    private List<String> types = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();

    /**
     * 是否是全局用户组
     */
    private Boolean global;
}
