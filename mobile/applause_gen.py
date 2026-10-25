# Gebruik: python3 applause_gen.py && ffmpeg -i applause.wav -c:a libvorbis -q:a 4 src/main/res/raw/applause.ogg
# Synthetisch applaus, realistischer: veel klappers met verschillende handhouding (dof tot scherp),
# afstand (ver weg = zachter en doffer), wisselend tempo, publieksgeroezemoes en zaalgalm.
import numpy as np, wave
rng = np.random.default_rng(11)
SR = 44100
DUR = 10.0
n = int(SR * DUR)

def fft_filter(x, mask_fn):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * mask_fn(f), len(x))

def peak(f, fc, q):
    # resonantie rond fc
    return 1 / (1 + ((f - fc) / (fc / q)) ** 2)

def clap(kind, far):
    """Eén klap: korte ruisstoot met resonantie. kind 0 = vlakke handen (dof), 1 = vingers (scherp)."""
    length = int(0.06 * SR)
    t = np.arange(length) / SR
    noise = rng.standard_normal(length)
    decay = np.exp(-t / rng.uniform(0.004, 0.009))
    attack = np.minimum(1, t / 0.0006)
    burst = noise * decay * attack
    fc = rng.uniform(700, 1300) if kind == 0 else rng.uniform(1600, 3200)
    q = rng.uniform(1.2, 2.5)
    def mask(f):
        m = 0.25 + peak(f, fc, q) + 0.4 * peak(f, fc * 2.1, q)
        m *= 1 / (1 + (120 / np.maximum(f, 1)) ** 4)          # geen gerommel
        m *= 1 / (1 + (f / (9000 - 6000 * far)) ** 3)         # ver weg = doffer
        return m
    return fft_filter(burst, mask)

# Bibliotheek van klapgeluiden, zodat niet elke klap hetzelfde klinkt
library = [(clap(k, far), far) for far in (0.0, 0.3, 0.6, 0.85) for k in (0, 1) for _ in range(6)]

out = np.zeros(n + SR)
people = 140
for p in range(people):
    far = rng.choice([0.0, 0.3, 0.6, 0.85], p=[0.08, 0.22, 0.35, 0.35])
    kind = rng.integers(0, 2)
    gain = (1.0 - 0.75 * far) * rng.uniform(0.6, 1.0)
    period = rng.uniform(0.2, 0.3)
    start = rng.gamma(2.0, 0.15)                     # iedereen begint snel na elkaar
    stop = rng.uniform(5.5, 9.4) if rng.random() < 0.85 else rng.uniform(9.0, 9.8)
    sounds = [s for s, fa in library if fa == far]
    tt = start
    while tt < stop:
        # tempo zakt iets aan het eind, enthousiasme ook
        slow = 1 + 0.25 * max(0, (tt - stop + 2.5) / 2.5)
        fade = min(1.0, (stop - tt) / 1.8)
        s = sounds[rng.integers(len(sounds))] if kind == 0 else sounds[rng.integers(len(sounds))]
        i = int(tt * SR)
        out[i:i + len(s)] += s * gain * fade * rng.uniform(0.7, 1.0)
        tt += period * slow * rng.uniform(0.9, 1.1)
out = out[:n]

# Geroezemoes van publiek: zachte, langzaam golvende ruis in het spraakgebied
murmur = fft_filter(rng.standard_normal(n), lambda f: peak(f, 450, 0.8) / (1 + (70 / np.maximum(f, 1)) ** 4))
wobble = 0.6 + 0.4 * fft_filter(rng.standard_normal(n), lambda f: (f < 3).astype(float))
murmur *= wobble / np.max(np.abs(murmur))

mix = out / np.max(np.abs(out)) + 0.06 * murmur

# Zaalgalm: vroege reflecties plus een staart van ongeveer een seconde
rev_len = int(1.2 * SR)
rt = np.arange(rev_len) / SR
ir = rng.standard_normal(rev_len) * np.exp(-rt / 0.28)
ir = fft_filter(ir, lambda f: 1 / (1 + (f / 4000) ** 2))
for d, g in [(0.011, 0.5), (0.019, 0.35), (0.031, 0.3), (0.047, 0.2)]:
    ir[int(d * SR)] += g * 40
wet = np.fft.irfft(np.fft.rfft(mix, 2 * n) * np.fft.rfft(ir, 2 * n), 2 * n)[:n]
mix = mix + 0.35 * wet / np.max(np.abs(wet)) * np.max(np.abs(mix))

env = np.ones(n)
fi = int(0.15 * SR); fo = int(0.8 * SR)
env[:fi] = np.linspace(0, 1, fi) ** 0.5
env[-fo:] = np.linspace(1, 0, fo) ** 2
mix *= env
# zachte compressie zodat het vol klinkt maar niet hard
mix = np.tanh(1.6 * mix / np.max(np.abs(mix))) 
mix = mix / np.max(np.abs(mix)) * 0.9
pcm = (mix * 32767).astype(np.int16)
with wave.open("applause.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
rms = [float(np.sqrt(np.mean(mix[i*SR:(i+1)*SR]**2))) for i in range(int(DUR))]
print("rms per seconde:", " ".join(f"{r:.2f}" for r in rms))
