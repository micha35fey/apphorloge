# Gebruik: python3 applause_gen.py && ffmpeg -i applause.wav -c:a libvorbis -q:a 4 src/main/res/raw/applause.ogg
# Synthetisch applaus, realistischer: veel klappers met verschillende handhouding (dof tot scherp),
# afstand, snel tempo, gejoel, publieksgeroezemoes, zaalgalm, fluiten en juichen.
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
people = 170
for p in range(people):
    far = rng.choice([0.0, 0.3, 0.6, 0.85], p=[0.08, 0.22, 0.35, 0.35])
    kind = rng.integers(0, 2)
    gain = (1.0 - 0.75 * far) * rng.uniform(0.6, 1.0)
    period = rng.uniform(0.15, 0.24)
    start = rng.gamma(2.0, 0.15)                     # iedereen begint snel na elkaar
    stop = rng.uniform(7.0, 9.5) if rng.random() < 0.85 else rng.uniform(9.2, 9.8)
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

# --- Fluiten: vingerfluit, een hoge toon die omhoog en weer omlaag glijdt, met wat adem ---
def whistle(length, f0):
    t = np.arange(int(length * SR)) / SR
    shape = rng.choice(["up", "updown", "double"])
    if shape == "up":
        pitch = f0 * (1 + 0.35 * np.minimum(1, t / (0.6 * length)))
    elif shape == "updown":
        pitch = f0 * (1 + 0.4 * np.sin(np.pi * t / length))
    else:
        pitch = f0 * (1 + 0.3 * (np.sin(2 * np.pi * t / length) > 0))
    pitch = pitch * (1 + 0.01 * np.sin(2 * np.pi * rng.uniform(5, 7) * t))
    phase = 2 * np.pi * np.cumsum(pitch) / SR
    tone = np.sin(phase) + 0.15 * np.sin(2 * phase)
    breath = fft_filter(rng.standard_normal(len(t)), lambda f: peak(f, f0 * 1.2, 3))
    env = np.minimum(1, t / 0.04) * np.minimum(1, (length - t) / 0.08)
    return (tone + 0.15 * breath / np.max(np.abs(breath))) * env

# --- Roepen: "woo!" en "hé!" uit een stemtoon (zaagtand) met klinkerformanten ---
VOWELS = {"oe": (320, 800, 2300), "a": (750, 1200, 2500), "e": (450, 1900, 2600)}
def shout(length, f0, vowels):
    t = np.arange(int(length * SR)) / SR
    # toonhoogte gaat omhoog en zakt aan het eind, zoals bij juichen
    contour = f0 * (1 + 0.45 * np.sin(np.pi * np.minimum(1, t / length) ** 0.7))
    contour = contour * (1 + 0.02 * np.sin(2 * np.pi * 6 * t))
    phase = np.cumsum(contour) / SR
    saw = 2 * (phase % 1) - 1
    voice = saw + 0.3 * fft_filter(rng.standard_normal(len(t)), lambda f: (f > 1500).astype(float))
    out = np.zeros(len(t))
    # klinker loopt over van de eerste naar de tweede (bijvoorbeeld w-oe naar a)
    a_form, b_form = VOWELS[vowels[0]], VOWELS[vowels[1]]
    mix_ab = np.minimum(1, t / (0.5 * length))
    for (fa, fb, bw) in zip(a_form, b_form, (90, 120, 160)):
        for frac in (0.0, 0.5, 1.0):
            fc = fa + (fb - fa) * frac
            band = fft_filter(voice, lambda f, fc=fc: 1 / (1 + ((f - fc) / bw) ** 2))
            weight = 1 - np.abs(mix_ab - frac) * 2
            out += band * np.clip(weight, 0, 1)
    env = np.minimum(1, t / 0.05) * np.minimum(1, (length - t) / 0.15) ** 1.5
    return out * env

def place(sound, at, gain, far):
    s = fft_filter(sound, lambda f: 1 / (1 + (f / (9000 - 6000 * far)) ** 3)) if far > 0 else sound
    s = s / np.max(np.abs(s)) * gain
    i = int(at * SR)
    end = min(n, i + len(s))
    mix[i:end] += s[: end - i]

# --- Gejoel: veel stemmen door elkaar, aanzwellend aan het begin ---
roar = np.zeros(n)
for fc in (500, 900, 1400, 2400):
    roar += fft_filter(rng.standard_normal(n), lambda f, fc=fc: 1 / (1 + ((f - fc) / (fc * 0.35)) ** 2))
tr = np.arange(n) / SR
swell = np.minimum(1, tr / 0.6) * np.exp(-np.maximum(0, tr - 1.2) / 2.8)
roar *= swell * (0.7 + 0.3 * wobble)
mix += 0.22 * roar / np.max(np.abs(roar))

for _ in range(10):
    place(whistle(rng.uniform(0.5, 1.1), rng.uniform(2000, 3000)),
          rng.uniform(0.2, 7.5), rng.uniform(0.15, 0.26), rng.choice([0.0, 0.3, 0.6]))
for _ in range(16):
    f0 = rng.choice([rng.uniform(130, 190), rng.uniform(220, 320)])   # mannen en vrouwen
    vowels = [("oe", "oe"), ("oe", "a"), ("e", "e"), ("a", "a")][rng.integers(4)]
    place(shout(rng.uniform(0.5, 1.2), f0, vowels),
          min(6.5, rng.gamma(2.0, 1.1)), rng.uniform(0.1, 0.18), rng.choice([0.3, 0.6, 0.85]))

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
