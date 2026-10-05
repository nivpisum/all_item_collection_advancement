"""Verify advancement semantics, compatibility and creative presentation data."""
from __future__ import annotations
import hashlib,json,tempfile,tomllib,zipfile
from pathlib import Path
import build,classification

def check(output):
    root=classification.read()
    legal=root.children[0]
    expected_data,stats=build.advancement_files(root)
    assets,tabs=build.creative_files(root)
    catalog=build.advancement_catalog(root)
    assets["assets/aica/advancement_catalog.json"]=build.json_bytes(catalog)
    zip_path=output/f"aica_{build.MINECRAFT}_{build.VERSION}.zip"
    jar_path=output/f"aica_{build.MINECRAFT}_forge_{build.VERSION}.jar"
    with zipfile.ZipFile(zip_path) as dp,zipfile.ZipFile(jar_path) as mod:
        assert dp.testzip() is None and mod.testzip() is None
        for name,payload in expected_data.items(): assert dp.read(name)==mod.read(name)==payload,name
        for name,payload in assets.items(): assert mod.read(name)==payload,name
        assert not any(n.endswith('.class') or n.startswith('assets/') for n in dp.namelist())
        assert any(n.endswith('AicaMod.class') for n in mod.namelist())
        assert not any(n.startswith(('forge/me/hypherionmc/','io/github/bizcub/')) for n in mod.namelist())
        metadata=tomllib.loads(mod.read('META-INF/mods.toml').decode('utf-8'))
        assert metadata['modLoader']=='javafml'
        assert metadata['mods'][0]['version']==build.VERSION=='1.3.0'
        assert metadata['mods'][0]['displayName']=='AICA - 全物品收集进度'
        assert all(word in metadata['mods'][0]['description'] for word in ('1146','创造栏','折叠','其他模组页'))
        assert metadata['mods'][0]['authors']=='nivpisum'
        assert mod.read(metadata['mods'][0]['logoFile'])==(classification.ROOT/'asset/AICA全物品收集进度徽章.png').read_bytes()
        dependencies={r['modId']:r for r in metadata['dependencies']['aica']}
        for name in ('morecreativetabs','inventory_item_groups'):
            assert dependencies[name]['mandatory'] and dependencies[name]['side']=='CLIENT'
        mixins=json.loads(mod.read('aica.mixins.json'))
        assert {'MoreCreativeTabsMixin','ItemGroupsMixin','CreativeScreenMixin','ClientAdvancementsMixin',
                'CreativeContentsMixin','AdvancementScreenRemovalMixin',
                'AdvancementTabMixin','AdvancementsScreenMixin','BetterAdvancementTabMixin',
                'BetterAdvancementsScreenMixin','BetterAdvancementTabTypeAccessor',
                'AdvancementWidgetMixin','BetterAdvancementWidgetMixin'}==set(mixins['client'])
        definitions={n.removeprefix('data/aica/advancements/').removesuffix('.json'):json.loads(dp.read(n))
                     for n in dp.namelist() if n.startswith('data/aica/advancements/')}
        old={n:json.loads(dp.read(n)) for n in dp.namelist() if n.startswith('data/all_item/advancements/')}
    visible={k:v for k,v in definitions.items() if 'display' in v}
    assert len(visible)==stats['visible_advancements']==721
    assert set(catalog['node_order'])=={'aica:'+key for key in visible} and len(catalog['node_order'])==721
    roots=[k for k,v in visible.items() if 'parent' not in v]
    assert set('aica:'+k for k in roots)=={t['id'] for t in catalog['tabs']} and len(roots)==28
    assert catalog['tabs'][0]['id']=='aica:'+root.advancement
    model={n.advancement:n for n in root.walk()}
    model.update({build.page_id(o):o for c in legal.children for o in c.children})
    for key,value in visible.items():
        node=model[key]
        items=legal.items if key==root.advancement else node.items
        assert list(value['criteria'])==items
        assert value['requirements']==[[i] for i in items]
        assert all(v['trigger']=='minecraft:inventory_changed' for v in value['criteria'].values())
        display=value['display']
        assert display['description'].startswith('获得') and not display['description'].endswith(('。','.'))
        expected_frame='challenge' if node.rank in {'界','门','纲','目','亚目'} else 'goal' if node.rank in {'科','亚科'} else 'task'
        assert display['frame']==expected_frame and not display['hidden']
        if key.startswith('page/'):
            assert not display['show_toast'] and not display['announce_to_chat']
        else:
            assert display['show_toast'] and display['announce_to_chat']
        if node.rank=='属': assert not any(v.get('parent')=='aica:'+key and 'display' in v for v in definitions.values())
        if 'parent' in value: assert value['parent'].removeprefix('aica:') in visible
    overview_ids={root.advancement,legal.advancement}|{c.advancement for c in legal.children}|{o.advancement for c in legal.children for o in c.children}
    assert len(overview_ids)==36 and all(model[k].rank in {'界','门','纲','目'} for k in overview_ids)
    assert set(legal.items).isdisjoint(root.children[1].items)
    for item in legal.items:
        predicate=json.loads(expected_data['data/aica/predicates/collected/'+item.removeprefix('minecraft:')+'.json'])
        assert predicate['condition']=='minecraft:any_of'
        actual_sources={key for term in predicate['terms']
                        for key,criteria in term['predicate']['type_specific']['advancements'].items()
                        if key.startswith('aica:') and criteria=={item:True}}
        expected_sources={'aica:'+key for key,value in visible.items()
                          if key.startswith('node/') and key not in {root.advancement,legal.advancement}
                          and item in value['criteria']}
        assert actual_sources==expected_sources and len(actual_sources)>=3,item
    for key,value in definitions.items():
        assert 'rewards' not in value
        if key.startswith('discovery/'):
            assert 'display' not in value and value['requirements']==[list(value['criteria'])]
            parent=value['parent'].removeprefix('aica:')
            assert set(value['criteria'])==set(visible[parent]['criteria'])
    assert len(old)==509 and all('display' not in v and 'rewards' not in v for v in old.values())
    for tab in catalog['tabs'][1:]:
        value=visible[tab['id'].removeprefix('aica:')]
        assert value['display']['background']==f"minecraft:textures/gui/advancements/backgrounds/{build.CLASS_BACKGROUNDS[tab['class']]}.png"
    previous=output/'aica_1.20.1_1.0.0.zip'
    if previous.exists():
        with zipfile.ZipFile(previous) as pack:
            for name in pack.namelist():
                if name.startswith('data/aica/advancements/node/'):
                    old_value=json.loads(pack.read(name))
                    new=definitions[name.removeprefix('data/aica/advancements/').removesuffix('.json')]
                    assert set(old_value['criteria'])==set(new['criteria']),name
    assert len(tabs)==11
    assert [t['name'] for t in tabs]==[n.name for _,n in build.creative_branches(root)]
    grouped=set()
    for tab,(_,branch) in zip(tabs,build.creative_branches(root)):
        assert tab['items']==branch.items
        expected=[n for n in branch.walk() if n.rank=='属' and len(n.items)>=3]
        assert len(tab['groups'])==len(expected)
        for group,node in zip(tab['groups'],expected):
            assert group['items']==node.items and group['icon']==node.icon
            assert grouped.isdisjoint(group['items']);grouped.update(group['items'])
    disabled=json.loads(assets['assets/aica/morecreativetabs/disabled_tabs.json'])['disabled_tabs']
    assert all('itemGroup.'+name not in disabled for name in ('search','hotbar','inventory'))
    assert 'itemGroup.op' in disabled
    assert 'morecreativetabs.daily_redstone' in disabled
    tab_order=json.loads(assets['assets/aica/morecreativetabs/ordered_tabs.json'])['tabs']
    keys=[tab['key'] for tab in tabs]
    assert tab_order==keys[:5]+['itemGroup.hotbar','itemGroup.search']+keys[5:]+['itemGroup.inventory']
    with tempfile.TemporaryDirectory(prefix='verify_release_',dir=output) as temp:
        assert build.write_archive(Path(temp)/'first.zip',expected_data)==build.write_archive(Path(temp)/'second.zip',expected_data)
    return {'passed':True,'version':build.VERSION,'author':'nivpisum',**stats,'overview_nodes':36,
            'all_visible_require_all':True,'no_advancement_rewards':True,'native_announcements':True,
            'separate_discovery_conditions':True,'stable_1_0_criterion_keys':True,
            'migration_reads_all_old_classification_criteria':True,
            'creative_tabs':11,'retained_native_utilities':['hotbar','search','inventory'],
            'catalogue_genera_with_at_least_three_ids':sum(len(t['groups']) for t in tabs),'zip_jar_shared_data_identical':True,
            'datapack_deterministic':True,'source_sha256':hashlib.sha256(classification.SOURCE.read_bytes()).hexdigest()}

if __name__=='__main__': print(json.dumps(check(classification.ROOT/'build'),ensure_ascii=False,indent=2))
