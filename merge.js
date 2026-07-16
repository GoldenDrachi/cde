const fs = require('fs');
const oldJson = JSON.parse(fs.readFileSync('old_lang.json', 'utf8').replace(/cdde/g, 'cde'));
const newJson = JSON.parse(fs.readFileSync('src/main/resources/assets/cde/lang/en_us.json', 'utf8'));
const merged = { ...oldJson, ...newJson };
fs.writeFileSync('src/main/resources/assets/cde/lang/en_us.json', JSON.stringify(merged, null, 2));
