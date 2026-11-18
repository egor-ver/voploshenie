from pathlib import Path
import shutil
root=Path.cwd()
for name in ('test_config','catalog','demo_answers'):
    target=root/'web'/'data'/f'{name}.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root/'app'/'src'/'main'/'assets'/f'{name}.json', target)
