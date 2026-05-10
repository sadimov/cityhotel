package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.RoleAdminDto;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.admin.RoleAdminMapper;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation read-only de {@link RoleAdminService}.
 */
@Service
@Transactional(readOnly = true)
public class RoleAdminServiceImpl implements RoleAdminService {

    private final RoleRepository roleRepository;
    private final RoleAdminMapper roleMapper;

    public RoleAdminServiceImpl(RoleRepository roleRepository, RoleAdminMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RoleAdminDto> findAll() {
        return roleRepository.findAll(Sort.by("roleNom"))
                .stream()
                .map(roleMapper::toDto)
                .toList();
    }

    @Override
    public RoleAdminDto findById(Integer roleId) {
        Role entity = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notFound"));
        return roleMapper.toDto(entity);
    }
}
