import * as bf from '@birdflop/rgbirdflop';
const base = { ...bf.rgbDefaults, text: 'Epee du Dragon', colors: [{hex:'#ff5555',pos:0},{hex:'#ffaa00',pos:50},{hex:'#55ffff',pos:100}] };
const out = {};
for (const [i, f] of bf.colorFormats.entries()) {
  out['plain_' + i] = bf.generateOutput({ ...base, colorFormat: f });
  out['bolditalic_' + i] = bf.generateOutput({ ...base, colorFormat: f, baseFormatting: { bold: true, italic: true } });
}
out.default = bf.generateOutput(bf.rgbDefaults);
console.log(JSON.stringify(out, null, 1));
