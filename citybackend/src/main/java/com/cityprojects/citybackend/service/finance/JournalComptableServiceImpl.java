package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.JournalComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableUpdateDto;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.JournalComptableMapper;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation de {@link JournalComptableService}.
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en
 *       ecriture pour les operations qui mutent.</li>
 *   <li>Validation des codes au format UPPERCASE alphanumerique (Pattern porte
 *       par {@link JournalComptableCreateDto}).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class JournalComptableServiceImpl implements JournalComptableService {

    private static final Logger logger = LoggerFactory.getLogger(JournalComptableServiceImpl.class);

    private final JournalComptableRepository repository;
    private final JournalComptableMapper mapper;

    public JournalComptableServiceImpl(JournalComptableRepository repository,
                                       JournalComptableMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public JournalComptableDto getOrCreate(String code, String libelle, TypeJournal type) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("error.journal.codeRequired");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new BusinessException("error.journal.libelleRequired");
        }
        if (type == null) {
            throw new BusinessException("error.journal.typeRequired");
        }
        Optional<JournalComptable> existing = repository.findByCode(code);
        if (existing.isPresent()) {
            return mapper.toDto(existing.get());
        }
        JournalComptable j = new JournalComptable();
        j.setCode(code);
        j.setLibelle(libelle);
        j.setType(type);
        j.setActif(Boolean.TRUE);
        // PAS de setHotelId : Hibernate via @TenantId.
        JournalComptable saved = repository.save(j);
        logger.info("Journal comptable cree (getOrCreate) : code={}, type={}", saved.getCode(), saved.getType());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public JournalComptableDto create(JournalComptableCreateDto dto) {
        String code = dto.code();
        if (repository.existsByCode(code)) {
            throw new BusinessException("error.journal.codeAlreadyExists");
        }
        JournalComptable j = new JournalComptable();
        j.setCode(code);
        j.setLibelle(dto.libelle());
        j.setType(dto.type());
        j.setActif(Boolean.TRUE);
        JournalComptable saved = repository.save(j);
        logger.info("Journal comptable cree : id={}, code={}", saved.getId(), saved.getCode());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public JournalComptableDto update(Long id, JournalComptableUpdateDto dto) {
        JournalComptable j = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));
        j.setLibelle(dto.libelle());
        j.setType(dto.type());
        JournalComptable saved = repository.save(j);
        logger.info("Journal comptable mis a jour : id={}, code={}", saved.getId(), saved.getCode());
        return mapper.toDto(saved);
    }

    @Override
    public JournalComptableDto findByCode(String code) {
        JournalComptable j = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));
        return mapper.toDto(j);
    }

    @Override
    public JournalComptableDto findById(Long id) {
        JournalComptable j = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));
        return mapper.toDto(j);
    }

    @Override
    public List<JournalComptableDto> findAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<JournalComptableDto> findActifs() {
        return repository.findByActifTrueOrderByCodeAsc().stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public JournalComptableDto desactiver(Long id) {
        JournalComptable j = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));
        if (Boolean.FALSE.equals(j.getActif())) {
            // Idempotent
            return mapper.toDto(j);
        }
        j.setActif(Boolean.FALSE);
        JournalComptable saved = repository.save(j);
        logger.info("Journal comptable desactive : id={}, code={}", saved.getId(), saved.getCode());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public JournalComptableDto reactiver(Long id) {
        JournalComptable j = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));
        if (Boolean.TRUE.equals(j.getActif())) {
            return mapper.toDto(j);
        }
        j.setActif(Boolean.TRUE);
        JournalComptable saved = repository.save(j);
        logger.info("Journal comptable reactive : id={}, code={}", saved.getId(), saved.getCode());
        return mapper.toDto(saved);
    }
}
