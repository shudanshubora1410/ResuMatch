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
        AliasIndex idx = new AliasIndex();
        List<AliasIndex.AliasRow> rows = new java.util.ArrayList<>();
        for (SkillEntity s : skills) {
            String canon = s.canonicalName == null ? "" : s.canonicalName.trim();
            if (canon.isEmpty()) continue;
            String n = com.resumatch.ats.TextNormalizer.skillKey(canon);
            if (!n.isEmpty()) rows.add(new AliasIndex.AliasRow(s.id, canon, n));
        }
        for (SkillAliasEntity a : aliases) {
            SkillEntity s = skills.stream().filter(x -> x.id == a.skillId).findFirst().orElse(null);
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
