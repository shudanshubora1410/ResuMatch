package com.resumatch.skills;

import com.resumatch.ats.AliasIndex;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AliasIndexService {
    private final SkillRepository skillRepo;
    private final SkillAliasRepository aliasRepo;
    private final AtomicReference<AliasIndex> index = new AtomicReference<>(new AliasIndex());

    public AliasIndexService(SkillRepository skillRepo, SkillAliasRepository aliasRepo) {
        this.skillRepo = skillRepo;
        this.aliasRepo = aliasRepo;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<SkillEntity> skills = skillRepo.findAll();
        List<SkillAliasEntity> aliases = aliasRepo.findAll();

        // Index skills by id for an O(1) lookup.
        //
        // The previous implementation did `skills.stream().filter(x -> x.id == a.skillId)`
        // inside the alias loop. That is an O(n*m) scan AND it compares Long
        // objects with `==` instead of `.equals()`, which only works by
        // accident for ids inside the Long cache (-128..127).
        java.util.Map<Long, SkillEntity> byId = new java.util.HashMap<>();
        for (SkillEntity s : skills) {
            if (s.id != null) byId.put(s.id, s);
        }

        AliasIndex idx = new AliasIndex();
        List<AliasIndex.AliasRow> rows = new java.util.ArrayList<>();
        for (SkillEntity s : skills) {
            String canon = s.canonicalName == null ? "" : s.canonicalName.trim();
            if (canon.isEmpty()) continue;
            String n = com.resumatch.ats.TextNormalizer.skillKey(canon);
            if (!n.isEmpty()) rows.add(new AliasIndex.AliasRow(s.id, canon, n));
        }
        for (SkillAliasEntity a : aliases) {
            SkillEntity s = byId.get(a.skillId);
            if (s == null) continue;
            String n = com.resumatch.ats.TextNormalizer.skillKey(a.aliasText);
            if (n.isEmpty()) continue;
            rows.add(new AliasIndex.AliasRow(s.id, s.canonicalName, n));
        }
        idx.load(rows);
        index.set(idx);
    }

    public AliasIndex get() { return index.get(); }
}
