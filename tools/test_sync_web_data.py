from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
for name in ('test_config', 'catalog', 'demo_answers'):
    source = ROOT / 'app' / 'src' / 'main' / 'assets' / f'{name}.json'
    copy = ROOT / 'web' / 'data' / f'{name}.json'
    assert source.read_bytes() == copy.read_bytes(), f'{copy} differs from {source}'
