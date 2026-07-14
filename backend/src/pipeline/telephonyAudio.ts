/**
 * Mu-law (G.711) ↔ linear16 PCM utilities for 8kHz telephony audio.
 */

const MULAW_BIAS = 0x84;
const MULAW_CLIP = 32635;

export function mulawToLinear16(mulaw: Buffer): Buffer {
  const out = Buffer.alloc(mulaw.length * 2);
  for (let i = 0; i < mulaw.length; i++) {
    const sample = mulawDecode(mulaw[i]);
    out.writeInt16LE(sample, i * 2);
  }
  return out;
}

export function linear16ToMulaw(pcm: Buffer): Buffer {
  const samples = pcm.length / 2;
  const out = Buffer.alloc(samples);
  for (let i = 0; i < samples; i++) {
    const sample = pcm.readInt16LE(i * 2);
    out[i] = mulawEncode(sample);
  }
  return out;
}

function mulawDecode(uval: number): number {
  uval = ~uval & 0xff;
  const sign = uval & 0x80;
  let exponent = (uval >> 4) & 0x07;
  let mantissa = uval & 0x0f;
  let sample = ((mantissa << 3) + MULAW_BIAS) << exponent;
  sample -= MULAW_BIAS;
  return sign ? -sample : sample;
}

function mulawEncode(sample: number): number {
  const sign = sample < 0 ? 0x80 : 0;
  if (sample < 0) sample = -sample;
  if (sample > MULAW_CLIP) sample = MULAW_CLIP;
  sample += MULAW_BIAS;

  let exponent = 7;
  for (let expMask = 0x4000; (sample & expMask) === 0 && exponent > 0; exponent--, expMask >>= 1) {
    /* find exponent */
  }

  const mantissa = (sample >> (exponent + 3)) & 0x0f;
  const encoded = ~(sign | (exponent << 4) | mantissa) & 0xff;
  return encoded;
}
