#!/usr/bin/env python3
"""Deterministic Spring source/config inventory from an immutable Git revision."""
from collections import Counter, defaultdict
from pathlib import Path
import argparse
import json
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[3]
SKIP = {'.git', 'target', '.run', 'repo', '.idea', 'docs'}
TEXT_SUFFIXES = {'.java', '.kt', '.kts', '.xml', '.properties', '.yml', '.yaml',
                 '.jsp', '.tag', '.tld', '.mf', '.sh', '.bat', '.conf', '.gradle'}
SPRING = re.compile(r'org\.springframework|springframework\.org|SpringBoot|SpringApplication|ApplicationContext|SecurityContextHolder|RestTemplate|spring\.factories')
TAGS = re.compile(r'<%@\s*taglib\s+prefix=["\']([^"\']+)["\']\s+uri=["\']([^"\']+)["\']')
TAG_USE = re.compile(r'<(spring|sec|form):([A-Za-z][A-Za-z0-9]*)\b')
PROPERTY_ALIAS = re.compile(r'(?<![A-Za-z0-9])(?:spring\.[a-z][A-Za-z0-9_-]*\.[A-Za-z0-9_.-]+|spring_[A-Za-z0-9_]+)')
PATTERNS = {
 'transaction': re.compile(r'@Transactional|TransactionTemplate|TransactionAspectSupport|PlatformTransactionManager|Isolation\.'),
 'cache': re.compile(r'@Cacheable|@CachePut|@CacheEvict|@Caching|@EnableCaching|CacheManager|CacheResolver'),
 'scheduling': re.compile(r'@Scheduled|@EnableScheduling|TaskScheduler|SchedulingConfigurer'),
 'lifecycle': re.compile(r'@PostConstruct|@PreDestroy|SmartLifecycle|DisposableBean|InitializingBean'),
 'method_security': re.compile(r'@Secured|@PreAuthorize|@PostAuthorize|@RolesAllowed|MethodSecurity|EnableGlobalMethodSecurity'),
 'di_annotations': re.compile(r'@Autowired|@Inject|@Qualifier|@Value|@Component|@Service|@Repository|@Configuration|@Bean|@Import|@ComponentScan'),
 'jdbc': re.compile(r'JdbcTemplate|NamedParameterJdbcTemplate|RowMapper|SimpleJdbc|JdbcDaoSupport|DataSourceUtils'),
 'reflection_and_factories': re.compile(r'Class\.forName|spring\.factories|org\.airsonic\.player\.spring\.migrations|SpringBootServletInitializer|SpringApplication'),
}

def source_files(revision):
    """Yield (repo-relative path, decoded lines) from git archive, never the worktree."""
    proc = subprocess.Popen(['git', 'archive', '--format=tar', revision], cwd=ROOT, stdout=subprocess.PIPE)
    assert proc.stdout is not None
    try:
        with tarfile.open(fileobj=proc.stdout, mode='r|') as archive:
            for member in archive:
                if not member.isfile():
                    continue
                rel = member.name
                path = Path(rel)
                if any(part in SKIP for part in path.parts):
                    continue
                if path.suffix.lower() not in TEXT_SUFFIXES and path.name not in {'Dockerfile', 'Jenkinsfile'}:
                    continue
                stream = archive.extractfile(member)
                if stream is None:
                    continue
                yield rel, stream.read().decode('utf-8', errors='replace').splitlines()
    except BaseException:
        proc.stdout.close()
        proc.wait()
        raise
    else:
        proc.stdout.close()
        code = proc.wait()
        if code:
            raise RuntimeError(f'git archive failed for {revision}: exit {code}')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--revision', default='HEAD', help='Git revision to scan (default: HEAD)')
    parser.add_argument('--output', type=Path, default=Path('docs/migration/spring-baseline/spring-inventory.json'),
                        help='Output JSON path, relative to repository root (default: baseline snapshot path)')
    args = parser.parse_args()
    revision = subprocess.check_output(['git', 'rev-parse', '--verify', f'{args.revision}^{{commit}}'], cwd=ROOT, text=True).strip()

    spring=[]; anns=defaultdict(list); tags=defaultdict(list); tag_uses=defaultdict(list)
    aliases=defaultdict(list); files_by_module=Counter()
    for rel, lines in source_files(revision):
        path = Path(rel)
        module = rel.split('/')[0] if '/' in rel else '(root)'
        for n, line in enumerate(lines, 1):
            if SPRING.search(line):
                spring.append({'path':rel,'line':n})
                files_by_module[module] += 1
            for category, rx in PATTERNS.items():
                if rx.search(line):
                    anns[category].append({'path':rel,'line':n})
            for alias in PROPERTY_ALIAS.findall(line):
                if alias.startswith('spring.migrations.'):
                    continue
                aliases[alias].append({'path':rel,'line':n})
            if path.suffix.lower() in {'.jsp','.tag'}:
                for prefix,uri in TAGS.findall(line):
                    tags[uri].append({'path':rel,'line':n,'prefix':prefix})
                for prefix,name in TAG_USE.findall(line):
                    tag_uses[prefix+':'+name].append({'path':rel,'line':n})
    result={
      'revision':revision,
      'scope':'Immutable Git archive of revision; excludes .git, target, .run, repo/ local Maven artifacts, .idea, docs, and all Markdown; text matches are line-based, not AST counts.',
      'spring_matches':spring,
      'spring_match_count':len(spring),
      'spring_match_lines_by_top_level_path':dict(sorted(files_by_module.items())),
      'annotation_and_api_matches':{k:v for k,v in sorted(anns.items())},
      'jsp_taglib_declarations':{k:v for k,v in sorted(tags.items())},
      'jsp_taglib_declaration_counts':dict(sorted((k,len(v)) for k,v in tags.items())),
      'jsp_tag_usage_counts':dict(sorted((k,len(v)) for k,v in tag_uses.items())),
      'jsp_tag_usages':dict(sorted(tag_uses.items())),
      'spring_property_aliases':{k:v for k,v in sorted(aliases.items())},
      'spring_property_alias_match_count':sum(map(len, aliases.values())),
      'spring_property_alias_note':'Configuration/environment aliases only; these matches do not imply Spring API/runtime binding.'
    }
    out=args.output if args.output.is_absolute() else ROOT/args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({'revision':revision,'spring_match_count':len(spring),
      'spring_files':len({x['path'] for x in spring}),
      'spring_matches_by_top_level_path':dict(sorted(files_by_module.items())),
      'taglib_declaration_counts':result['jsp_taglib_declaration_counts'],
      'tag_usage_counts':result['jsp_tag_usage_counts'],
      'spring_property_alias_match_count':result['spring_property_alias_match_count'],
      'annotation_api_counts':{k:len(v) for k,v in sorted(anns.items())},
      'snapshot':str(out.relative_to(ROOT)) if out.is_relative_to(ROOT) else str(out)},ensure_ascii=False,indent=2))
if __name__=='__main__': main()
