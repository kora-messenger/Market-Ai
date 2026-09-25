/**
 * MarketScope AI Learning Hub — original educational content.
 *
 * Every string below is written from scratch for MarketScope AI. Category
 * names, pattern names and colour-tag styling are standard, decades-old
 * technical-analysis terminology (nobody owns "Head and Shoulders" or
 * "Doji") — the explanations, analogies, trade steps and diagrams are ours.
 *
 * Each pattern renders as a colour-tagged card in the hub grid and expands
 * into a full lesson: what it is, how to spot it, the psychology behind it,
 * a labelled diagram (drawn live in Compose — no static images), how to
 * trade it, a quick cheat-sheet, and common mistakes.
 *
 * diagram.kind is either:
 *  - "line": a price path (array of [x,y], 0-1 normalized, y grows downward
 *    i.e. 0 = top of chart = higher price). Optional `neckline` / `trend1`
 *    / `trend2` (each [[x,y],[x,y]]) draw dashed reference lines. Optional
 *    `zone` shades a rectangle. `markers` label specific point indices.
 *  - "candles": an array of OHLC glyphs { x, high, bodyTop, bodyBottom, low,
 *    bullish }. Optional `zone` shades a rectangle behind the candles.
 */

// Three learning tracks in one hub: chart/candlestick/SMC patterns apply to
// any tradable market (Trading), while Crypto and Stocks each get their own
// asset-specific fundamentals so a crypto-only or stock-only reader isn't
// stuck reading pure forex/futures pattern lessons.
const TRACKS = [
  { id: "trading", label: "Trading" },
  { id: "crypto", label: "Crypto" },
  { id: "stocks", label: "Stocks" }
];

const CATEGORIES = [
  { id: "chart", label: "Chart Patterns", track: "trading" },
  { id: "candlestick", label: "Candlestick Patterns", track: "trading" },
  { id: "smc", label: "Smart Money Concepts", track: "trading" },
  { id: "crypto-fundamentals", label: "Crypto Fundamentals", track: "crypto" },
  { id: "crypto-mechanics", label: "Crypto Trading Mechanics", track: "crypto" },
  { id: "stock-fundamentals", label: "Stock Fundamentals", track: "stocks" },
  { id: "stock-events", label: "Company Events", track: "stocks" }
];

const PATTERNS = [
  // ---------------------------------------------------------------- CHART
  {
    slug: "head-and-shoulders",
    title: "Head and Shoulders",
    category: "chart",
    track: "trading",
    accent: "violet",
    bias: "bearish",
    tagline: "Three peaks, the middle one tallest — a classic top reversal.",
    whatItIs:
      "A Head and Shoulders forms after a sustained uptrend, when buyers push price to a new high (the head), pull back, but then fail to make a higher high on a second attempt (the right shoulder) after already having made a first, similar high (the left shoulder). The failure to repeat the head's high is the tell: demand is running out of road.",
    howToSpot: [
      "An established uptrend feeding into the pattern",
      "Left shoulder: a peak followed by a pullback",
      "Head: a higher peak, then another pullback to roughly the same level as the first pullback",
      "Right shoulder: a peak that's noticeably lower than the head, ideally close to the left shoulder's height",
      "A 'neckline' connecting the two pullback lows — the line price needs to break for the pattern to trigger"
    ],
    psychology:
      "Each peak is a wave of buyers stepping in — but the second pullback shows sellers defending the same price zone twice, and the failed third push (the right shoulder) shows buyers are no longer strong enough to make new highs. Once price breaks the neckline, the traders who bought near the top are now underwater and start selling too, which is what turns a stall into an actual decline.",
    analogy: {
      title: "Like three waves at the beach",
      body:
        "Picture three waves rolling in. The first two reach similar points up the sand. The middle one, though, is the biggest — it goes further than the other two. The third wave rolls in weaker than the second, barely matching the first. Anyone watching would say the tide is turning, not building. That weakening third push is your right shoulder."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.78], [0.16, 0.32], [0.32, 0.6], [0.5, 0.08], [0.68, 0.6], [0.84, 0.34], [1, 0.82]],
      neckline: [[0.05, 0.62], [0.95, 0.63]],
      markers: [
        { index: 1, label: "Left shoulder" },
        { index: 3, label: "Head" },
        { index: 5, label: "Right shoulder" }
      ]
    },
    howToTrade: [
      "Wait for a full close below the neckline — don't front-run the shoulder forming",
      "A retest of the broken neckline from below (now acting as resistance) is the higher-probability, lower-drama entry",
      "Stop loss above the right shoulder's high",
      "Target the height of the head-to-neckline distance, projected downward from the breakout point",
      "Use a 1:2 minimum reward-to-risk; scale out into obvious support if price stalls early"
    ],
    cheatSheet: { entry: "Neckline break + retest", stopLoss: "Above right shoulder", target: "Head-to-neckline height, projected down", timeframes: "4H and Daily", bias: "Bearish reversal" },
    mistakes: [
      "Selling the moment the right shoulder appears, before the neckline actually breaks",
      "Ignoring an unusually deep or shallow right shoulder — the more symmetrical the shoulders, the more reliable the pattern",
      "Forgetting this only counts as a reversal pattern after a real uptrend — the same shape mid-range means far less"
    ]
  },
  {
    slug: "double-top-bottom",
    title: "Double Top / Double Bottom",
    category: "chart",
    track: "trading",
    accent: "rose",
    bias: "bearish",
    tagline: "Two failed attempts at the same level — momentum giving up.",
    whatItIs:
      "A Double Top is two peaks at roughly the same price with a pullback between them; a Double Bottom is the mirror image at the bottom of a downtrend — two lows at a similar level with a bounce between them. Both mark a level the market tried twice to break through and couldn't, which is a strong sign the prevailing trend has run out of energy.",
    howToSpot: [
      "A clear prior trend (up for a top, down for a bottom) leading into the first peak or trough",
      "A retracement of at least a few percent between the two extremes — not just a flat pause",
      "The second peak/trough landing close to the first (a small overshoot or undershoot is normal)",
      "A 'confirmation line' at the low between the two tops (or the high between the two bottoms) that price must break to validate the pattern"
    ],
    psychology:
      "The first peak attracts sellers who think the run is done. Price pulls back, then buyers try again — and if they can't push past the same ceiling a second time, everyone watching that level now agrees it's real resistance. That shared belief is self-fulfilling: the next move down attracts even more sellers, because the failed second attempt basically announces that the buyers who mattered have already used their ammunition.",
    analogy: {
      title: "Knocking on a locked door twice",
      body:
        "You knock once, nobody answers, you step back. You try again a little later, same spot — still nothing. After the second unanswered knock, you stop trying and walk away. The market does the same thing at a double top: two failed attempts at the same door, and the crowd gives up and turns around."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.72], [0.24, 0.18], [0.48, 0.55], [0.72, 0.18], [1, 0.8]],
      neckline: [[0.1, 0.56], [0.9, 0.57]],
      markers: [
        { index: 1, label: "Top 1" },
        { index: 3, label: "Top 2" }
      ]
    },
    howToTrade: [
      "Wait for the confirmation line (the swing between the two peaks/troughs) to break with a full candle close",
      "Enter on the break, or on a retest of that line if you'd rather see it hold as new resistance/support",
      "Stop loss beyond the more recent of the two peaks/troughs",
      "Target the height from the peaks down to the confirmation line, projected in the breakout direction",
      "Double bottoms trade the same way in reverse — buy the breakout above the swing high between the two lows"
    ],
    cheatSheet: { entry: "Break of the middle swing point", stopLoss: "Beyond the second peak/trough", target: "Pattern height projected from the break", timeframes: "1H to Daily", bias: "Reversal (either direction)" },
    mistakes: [
      "Calling any two similar highs a double top — without a real pullback between them it's just a range",
      "Skipping the confirmation break and entering on the second peak alone, which fails often",
      "Using a stop that's too tight — the second peak/trough sometimes pokes slightly past the first before turning"
    ]
  },
  {
    slug: "rising-wedge",
    title: "Rising Wedge",
    category: "chart",
    track: "trading",
    accent: "rose",
    bias: "bearish",
    tagline: "Price grinds higher inside a narrowing, upward-sloping channel.",
    whatItIs:
      "A rising wedge is when price keeps making higher highs and higher lows, but the two trendlines connecting them are converging rather than running parallel — the rally is losing steam even as it technically continues. It usually resolves with a break to the downside, making it a bearish pattern despite forming during an up-move.",
    howToSpot: [
      "Both the swing highs and swing lows are rising",
      "The upper trendline (connecting the highs) is climbing more slowly than the lower trendline (connecting the lows) — the two lines are squeezing together",
      "Volume often fades as the wedge tightens",
      "Each new high tends to be a smaller push than the one before it"
    ],
    psychology:
      "Buyers are still technically in control — price is going up — but they're having to work harder for less distance each time, which is exactly what happens when a move is running out of genuine demand and coasting on momentum alone. The tightening range also squeezes out any traders using wide stops, so when the break finally comes, there's less support standing in its way.",
    analogy: {
      title: "A crowd getting squeezed toward an exit",
      body:
        "Think of a hallway that narrows as you walk down it. At first there's plenty of room to move forward, but the walls keep closing in. Eventually there's nowhere left to go but to stop, turn, and funnel back out the way you came. That narrowing hallway is the wedge; the sudden reversal is the break."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.86], [0.18, 0.58], [0.34, 0.66], [0.52, 0.4], [0.68, 0.48], [0.86, 0.24], [1, 0.55]],
      trend1: [[0, 0.62], [0.86, 0.17]],
      trend2: [[0, 0.9], [0.86, 0.42]],
      markers: [{ index: 6, label: "Breakdown" }]
    },
    howToTrade: [
      "Wait for a close below the lower (support) trendline of the wedge",
      "A retest of the broken trendline from underneath adds confirmation",
      "Stop loss just above the most recent swing high inside the wedge",
      "Target the width of the wedge at its widest point, projected down from the break",
      "Don't try to short inside the wedge just because it 'looks tired' — trade the break, not the shape"
    ],
    cheatSheet: { entry: "Close below lower trendline", stopLoss: "Above last swing high in the wedge", target: "Wedge's widest width, projected down", timeframes: "4H and Daily", bias: "Bearish, even though price was rising" },
    mistakes: [
      "Confusing a rising wedge with a healthy, parallel up-channel — the convergence of the two lines is the whole signal",
      "Shorting the first small pullback instead of waiting for an actual trendline break",
      "Ignoring the wedge if it appears mid-trend rather than after an extended run — context still matters"
    ]
  },
  {
    slug: "falling-wedge",
    title: "Falling Wedge",
    category: "chart",
    track: "trading",
    accent: "emerald",
    bias: "bullish",
    tagline: "Price grinds lower inside a narrowing, downward-sloping channel.",
    whatItIs:
      "The mirror image of a rising wedge: price makes lower highs and lower lows, but the two trendlines are converging as the decline loses momentum. It typically resolves with a break to the upside, making it a bullish pattern even though it forms during a downtrend.",
    howToSpot: [
      "Both swing highs and swing lows are falling",
      "The lower trendline (connecting the lows) is descending more slowly than the upper trendline (connecting the highs)",
      "Each new low is a smaller move than the one before it",
      "Selling volume typically dries up as the wedge tightens"
    ],
    psychology:
      "Sellers are technically still winning — price keeps dropping — but they need less and less effort to do it, which is the signature of exhaustion rather than strength. As the range compresses, the pool of sellers willing to keep pushing at worse and worse prices shrinks, and it only takes a modest wave of buying to flip the move.",
    analogy: {
      title: "A ball bouncing to a stop",
      body:
        "Drop a ball and watch it bounce. Each bounce is lower than the last, but also smaller and slower — the ball is running out of energy. Eventually it stops falling and just sits there, ready to be picked back up. The falling wedge is that decelerating bounce pattern on a price chart, right before the 'pick up' — the breakout."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.14], [0.18, 0.42], [0.34, 0.34], [0.52, 0.58], [0.68, 0.5], [0.86, 0.72], [1, 0.4]],
      trend1: [[0, 0.38], [0.86, 0.66]],
      trend2: [[0, 0.1], [0.86, 0.55]],
      markers: [{ index: 6, label: "Breakout" }]
    },
    howToTrade: [
      "Wait for a close above the upper (resistance) trendline of the wedge",
      "A retest of the broken trendline from above adds confirmation",
      "Stop loss just below the most recent swing low inside the wedge",
      "Target the width of the wedge at its widest point, projected up from the break",
      "Combine with an oversold reading elsewhere (e.g. RSI) for extra confluence, but don't require it"
    ],
    cheatSheet: { entry: "Close above upper trendline", stopLoss: "Below last swing low in the wedge", target: "Wedge's widest width, projected up", timeframes: "4H and Daily", bias: "Bullish, even though price was falling" },
    mistakes: [
      "Buying the first bounce inside the wedge instead of waiting for the real trendline break",
      "Mistaking a normal parallel downtrend channel for a falling wedge — the lines must be converging",
      "Setting a target that ignores nearby resistance from before the downtrend even started"
    ]
  },
  {
    slug: "symmetrical-triangle",
    title: "Symmetrical Triangle",
    category: "chart",
    track: "trading",
    accent: "cyan",
    bias: "neutral",
    tagline: "Falling highs meet rising lows — a coiled spring, direction unknown.",
    whatItIs:
      "A symmetrical triangle forms when swing highs are falling and swing lows are rising at roughly the same rate, squeezing price into a tightening range that comes to a point. Unlike a wedge, the two trendlines slope in opposite directions rather than the same one — which is why this pattern doesn't lean bullish or bearish on its own. It's a pause, not a directional bet, until it breaks.",
    howToSpot: [
      "At least two lower highs and two higher lows, each pair getting closer together",
      "The upper trendline slopes down, the lower trendline slopes up",
      "Volume typically contracts steadily as the triangle narrows",
      "Price action gets choppier and the swings get smaller the closer you get to the apex"
    ],
    psychology:
      "Neither buyers nor sellers can push the range in their favour — every rally gets sold a little sooner, every dip gets bought a little sooner, which is exactly what indecision looks like on a chart. That indecision doesn't last forever, though: the range keeps shrinking until one side finally overwhelms the other, usually with a burst of volume.",
    analogy: {
      title: "A coiled spring",
      body:
        "Squeeze a spring and it doesn't just sit there quietly — energy is building even while nothing visibly moves. The longer you compress it, the more force it releases the instant you let go, and there's no telling in advance which way it'll fly. That's a symmetrical triangle: compression now, a sudden release later, direction decided at the moment of the break, not before."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.15], [0.16, 0.75], [0.32, 0.28], [0.48, 0.62], [0.64, 0.38], [0.8, 0.52], [1, 0.45]],
      trend1: [[0, 0.18], [0.8, 0.44]],
      trend2: [[0, 0.78], [0.8, 0.5]],
      markers: [{ index: 6, label: "Breakout (either way)" }]
    },
    howToTrade: [
      "Don't guess the direction — wait for a decisive close beyond either trendline",
      "Enter in the direction of the break, ideally with a visible pickup in volume",
      "Stop loss on the opposite side of the triangle, near the most recent swing point",
      "Target the height of the triangle at its widest point, projected from the breakout",
      "If price just drifts out of the apex with no real push, treat it as a false break and stand aside"
    ],
    cheatSheet: { entry: "Break of either trendline", stopLoss: "Opposite side of the triangle", target: "Triangle's widest height, projected out", timeframes: "1H to Daily", bias: "Neutral until it breaks" },
    mistakes: [
      "Picking a side early because the recent trend 'should' continue — the pattern is genuinely two-sided",
      "Trading a breakout very close to the apex, where false breaks are more common",
      "Forgetting the triangle can simply expire — if price is still inside it near the apex with no clean break, the setup is gone"
    ]
  },
  {
    slug: "ascending-triangle",
    title: "Ascending Triangle",
    category: "chart",
    track: "trading",
    accent: "emerald",
    bias: "bullish",
    tagline: "A flat ceiling, a rising floor — buyers slowly winning the fight.",
    whatItIs:
      "An ascending triangle has a flat resistance line at the top (the same high gets tested repeatedly) and a rising support line at the bottom (each pullback finds buyers a little higher than the last). Unlike the symmetrical triangle, this shape does lean bullish, because it shows buyers steadily gaining ground while sellers only manage to defend one fixed price.",
    howToSpot: [
      "Two or more highs landing at almost exactly the same level",
      "Two or more lows, each one higher than the last",
      "The flat top and the rising bottom converge toward the right",
      "Usually appears during or after an uptrend, as a continuation shape"
    ],
    psychology:
      "Sellers keep showing up at the same price and keep getting overwhelmed — but they're not retreating any faster than that, which is why the ceiling stays flat instead of falling. Meanwhile buyers are getting more aggressive, willing to pay higher and higher prices on every dip rather than waiting for a discount. That imbalance — patient sellers, impatient buyers — tends to resolve upward once the flat ceiling finally gives way.",
    analogy: {
      title: "Water rising against a dam",
      body:
        "Water keeps rising behind a dam that isn't getting any taller. Every day the water level is a little higher than the day before, even though the dam itself hasn't moved. Eventually the water simply goes over the top — not because the dam weakened, but because the pressure behind it kept building."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.68], [0.18, 0.28], [0.34, 0.5], [0.52, 0.28], [0.68, 0.42], [0.84, 0.28], [1, 0.12]],
      trend1: [[0, 0.28], [0.84, 0.28]],
      trend2: [[0, 0.72], [0.84, 0.42]],
      markers: [{ index: 6, label: "Breakout" }]
    },
    howToTrade: [
      "Wait for a close above the flat resistance line",
      "A retest of that line from above, now acting as support, is the safer entry",
      "Stop loss below the most recent rising low",
      "Target the height of the triangle projected upward from the breakout",
      "Look for a volume pickup on the breakout candle to separate a real break from a fakeout"
    ],
    cheatSheet: { entry: "Close above the flat resistance", stopLoss: "Below the most recent rising low", target: "Triangle height, projected up", timeframes: "1H to Daily", bias: "Bullish continuation" },
    mistakes: [
      "Buying every touch of the rising support line hoping it breaks up eventually — trade the breakout, not the bounce",
      "Ignoring repeated false breaks above resistance before the real one — some patience is normal here",
      "Treating it as bullish in a clear downtrend context — the same shape after a big decline deserves more scrutiny"
    ]
  },

  // ------------------------------------------------------------- CANDLESTICK
  {
    slug: "bullish-engulfing",
    title: "Bullish Engulfing",
    category: "candlestick",
    track: "trading",
    accent: "emerald",
    bias: "bullish",
    tagline: "A big green candle completely swallows the red candle before it.",
    whatItIs:
      "A two-candle pattern that appears after a decline: a small down (red) candle is followed by a larger up (green) candle whose body fully covers the previous candle's body, open to close. It shows buyers didn't just show up — they overwhelmed the sellers from the candle before, in the same session.",
    howToSpot: [
      "Price has been falling or pulling back into the pattern",
      "First candle: a red body, ideally not huge",
      "Second candle: a green body that opens at or below the first candle's close and closes above the first candle's open",
      "The second candle's body should clearly engulf the first's — a marginal overlap is a weak signal"
    ],
    psychology:
      "Sellers push the market down on the first candle, business as usual. Then on the very next candle, buyers step in hard enough to erase the entire previous candle's move and then some — that's not a gentle shift, it's a takeover. Anyone who sold near the top of that first red candle is now watching price blow straight past their entry, which often pulls in more buying as shorts cover.",
    analogy: {
      title: "A comeback goal right after halftime",
      body:
        "A team's been losing the whole first half — no goals, low energy, the crowd's gone quiet. Then thirty seconds into the second half, they score a goal that not only ties the game but makes it feel like they're now the stronger side. That single goal doesn't just cancel the deficit — it changes the entire mood of the match. That's what an engulfing candle does to a chart."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.35, high: 0.35, bodyTop: 0.4, bodyBottom: 0.55, low: 0.58, bullish: false },
        { x: 0.65, high: 0.3, bodyTop: 0.32, bodyBottom: 0.76, low: 0.8, bullish: true }
      ]
    },
    howToTrade: [
      "Best used after a genuine downtrend or pullback, ideally into a support zone",
      "Enter on the close of the engulfing candle, or on the next candle's open for a small confirmation buffer",
      "Stop loss below the low of the engulfing candle",
      "Target the next resistance level or use a fixed 1:2 reward-to-risk",
      "Extra confidence if the engulfing candle also comes with higher-than-average volume"
    ],
    cheatSheet: { entry: "Close of the engulfing candle", stopLoss: "Below the engulfing candle's low", target: "Next resistance / 1:2 R:R", timeframes: "4H and Daily", bias: "Bullish reversal" },
    mistakes: [
      "Trading it in the middle of a range with no prior downtrend to reverse",
      "Counting a marginal, barely-engulfing candle the same as a decisive one",
      "Ignoring the next candle — if it immediately gives back the gain, the signal has failed and should be respected, not hoped away"
    ]
  },
  {
    slug: "bearish-engulfing",
    title: "Bearish Engulfing",
    category: "candlestick",
    track: "trading",
    accent: "rose",
    bias: "bearish",
    tagline: "A big red candle completely swallows the green candle before it.",
    whatItIs:
      "The mirror of the bullish version: after a rally, a small green candle is followed by a larger red candle that fully covers the previous candle's body. It signals sellers have taken over the session decisively, right after buyers had control.",
    howToSpot: [
      "Price has been rising into the pattern",
      "First candle: a green body",
      "Second candle: a red body that opens at or above the first candle's close and closes below the first candle's open",
      "The bigger the second candle relative to the first, the stronger the signal"
    ],
    psychology:
      "Buyers get a small win on the first candle, and it looks like the rally continues. Then, in the very next candle, sellers erase that entire gain and push further — a clean reversal of control rather than a gradual fade. Traders who bought near the top of the green candle are now trapped, and their eventual selling can accelerate the move down.",
    analogy: {
      title: "Winning the point, losing the game",
      body:
        "A tennis player wins a game point to close the gap, feeling momentum shift their way. On the very next point, their opponent hits back so hard they not only take the point but visibly seize control of the whole match. The small win is instantly overshadowed — that's the psychological flip a bearish engulfing candle represents."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.35, high: 0.58, bodyTop: 0.42, bodyBottom: 0.55, low: 0.62, bullish: true },
        { x: 0.65, high: 0.22, bodyTop: 0.25, bodyBottom: 0.7, low: 0.74, bullish: false }
      ]
    },
    howToTrade: [
      "Best used after a genuine uptrend or rally, ideally into a resistance zone",
      "Enter on the close of the engulfing candle, or the next candle's open for confirmation",
      "Stop loss above the high of the engulfing candle",
      "Target the next support level or use a fixed 1:2 reward-to-risk",
      "A volume spike on the red candle adds confidence"
    ],
    cheatSheet: { entry: "Close of the engulfing candle", stopLoss: "Above the engulfing candle's high", target: "Next support / 1:2 R:R", timeframes: "4H and Daily", bias: "Bearish reversal" },
    mistakes: [
      "Treating it as significant without a real rally beforehand to reverse",
      "Ignoring the size difference — a red candle that barely edges past the green one is a weak version of this pattern",
      "Entering without a stop above the pattern high, then holding through an invalidation because 'it should still work'"
    ]
  },
  {
    slug: "bullish-pin-bar",
    title: "Bullish Pin Bar",
    category: "candlestick",
    track: "trading",
    accent: "emerald",
    bias: "bullish",
    tagline: "A long lower wick, tiny body — sellers pushed down and got rejected.",
    whatItIs:
      "Also called a hammer, this single-candle pattern has a long lower wick, little to no upper wick, and a small body sitting near the top of the candle's range. It shows price was pushed sharply lower during the session but buyers fought all the way back, rejecting the lows.",
    howToSpot: [
      "Appears after a decline, ideally into a support level",
      "A lower wick at least twice the length of the body",
      "A small body near the top of the candle",
      "Little to no upper wick"
    ],
    psychology:
      "Sellers dominate the first part of the session, dragging price well below where it opened. But before the candle closes, buyers absorb all of that selling and drive price back up near the open — meaning every seller who acted during that drop is now sitting on a loss the moment the candle closes. That reversal of fortune is exactly what invites more buying on the next candle.",
    analogy: {
      title: "Bouncing off the floor",
      body:
        "Someone stumbles and falls hard toward the ground — for a moment it looks bad. But instead of staying down, they catch themselves at the very last second and spring back up almost to where they started. The long wick is the fall; the small body near the top is the recovery. Onlookers remember the bounce, not the stumble."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.5, high: 0.35, bodyTop: 0.38, bodyBottom: 0.5, low: 0.85, bullish: true }
      ]
    },
    howToTrade: [
      "Strongest at a known support level, round number, or trendline touch",
      "Enter on the next candle's open, or wait for that next candle to close higher for confirmation",
      "Stop loss just below the wick's low",
      "Target the next resistance level or a 1:2 reward-to-risk minimum",
      "Best on 4H and Daily charts — noisy on very short timeframes"
    ],
    cheatSheet: { entry: "Next candle open (or confirmed close higher)", stopLoss: "Below the wick low", target: "Next resistance / 1:2 R:R", timeframes: "4H and Daily", bias: "Bullish reversal" },
    mistakes: [
      "Buying a pin bar with no nearby support — context is most of the signal's value",
      "Using a stop that's too tight given how far the wick actually reached",
      "Treating every long lower wick as a hammer regardless of body size — the body must stay genuinely small"
    ]
  },
  {
    slug: "bearish-pin-bar",
    title: "Bearish Pin Bar",
    category: "candlestick",
    track: "trading",
    accent: "rose",
    bias: "bearish",
    tagline: "A long upper wick, tiny body — buyers pushed up and got rejected.",
    whatItIs:
      "Also called a shooting star, this single-candle pattern has a long upper wick, little to no lower wick, and a small body near the bottom of the candle's range. Price rallied hard during the session, then gave almost all of it back before the close, rejecting the highs.",
    howToSpot: [
      "Appears after a rally, ideally into a resistance level",
      "An upper wick at least twice the length of the body",
      "A small body near the bottom of the candle",
      "Little to no lower wick"
    ],
    psychology:
      "Buyers control the early part of the session and push price to a fresh high — but sellers step in hard enough to erase most of that gain by the close. Every buyer who chased that intraday high is now underwater, and their eventual exit selling is what often fuels the next leg down.",
    analogy: {
      title: "Jumping for a dunk that gets blocked",
      body:
        "A player leaps for what looks like an easy dunk, gets right up to the rim — and gets swatted straight back down before the ball goes in. The height of the jump is the long wick; the sudden fall back to the floor is the small body near the bottom. The attempt looked strong for a second, but it ended in a clean rejection."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.5, high: 0.15, bodyTop: 0.5, bodyBottom: 0.62, low: 0.66, bullish: false }
      ]
    },
    howToTrade: [
      "Strongest at a known resistance level, round number, or trendline touch",
      "Enter on the next candle's open, or wait for a confirmed lower close",
      "Stop loss just above the wick's high",
      "Target the next support level or a 1:2 reward-to-risk minimum",
      "Best on 4H and Daily charts, where the rejection carries more weight"
    ],
    cheatSheet: { entry: "Next candle open (or confirmed close lower)", stopLoss: "Above the wick high", target: "Next support / 1:2 R:R", timeframes: "4H and Daily", bias: "Bearish reversal" },
    mistakes: [
      "Shorting into thin air with no resistance nearby to justify the rejection",
      "Confusing it with a doji — a pin bar needs a small body clearly off to one side, not centred",
      "Skipping confirmation on lower timeframes where wicks form and fail constantly"
    ]
  },
  {
    slug: "doji",
    title: "Doji",
    category: "candlestick",
    track: "trading",
    accent: "cyan",
    bias: "neutral",
    tagline: "Open and close almost identical — a tug-of-war, nobody wins.",
    whatItIs:
      "A doji is a candle where the open and close are nearly the same price, leaving little to no body, regardless of how long the wicks on either side are. It represents pure indecision — buyers and sellers fought to a standstill during that period.",
    howToSpot: [
      "A tiny or almost non-existent body",
      "Wicks can be short, long, or one-sided — the defining feature is the open ≈ close",
      "Most meaningful after an extended trend, where it can mark hesitation before a turn",
      "Least meaningful in a choppy, directionless range, where it's just more noise"
    ],
    psychology:
      "Neither side could win the session. If it shows up after a strong trend, it often means the dominant side (buyers in an uptrend, sellers in a downtrend) has stopped pressing their advantage — which doesn't guarantee a reversal, but it's a signal that momentum has paused and the next candle deserves attention.",
    analogy: {
      title: "A rope stuck in a tug-of-war",
      body:
        "Two teams are pulling a rope in opposite directions with equal force. The marker on the rope doesn't move — not because nobody's pulling, but because both sides are pulling equally hard. That frozen middle marker is the doji: real effort on both sides, cancelling out to a standstill."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.5, high: 0.22, bodyTop: 0.48, bodyBottom: 0.53, low: 0.78, bullish: true }
      ]
    },
    howToTrade: [
      "Don't trade a doji alone — treat it as a flag to watch the next candle closely",
      "After an uptrend: a lower close on the next candle adds weight to a possible top",
      "After a downtrend: a higher close on the next candle adds weight to a possible bottom",
      "Combine with a nearby support/resistance level for the signal to mean anything",
      "In a flat range, mostly ignore it — indecision candles are common and low-value there"
    ],
    cheatSheet: { entry: "Wait for next candle's direction", stopLoss: "Beyond the doji's wick, on the confirming side", target: "Context-dependent — nearest level", timeframes: "4H and Daily", bias: "Neutral / pause signal" },
    mistakes: [
      "Trading the doji itself instead of waiting for confirmation",
      "Treating every small-bodied candle as a 'true' doji when the open/close gap is actually meaningful",
      "Over-weighting a doji that appears with no trend behind it"
    ]
  },
  {
    slug: "morning-star",
    title: "Morning Star",
    category: "candlestick",
    track: "trading",
    accent: "emerald",
    bias: "bullish",
    tagline: "Big red candle, a small pause, then a big green candle — dawn breaking.",
    whatItIs:
      "A three-candle bullish reversal: a large down candle, followed by a small-bodied candle that gaps or dips lower (showing the decline is losing force), followed by a large up candle that closes well back into the first candle's range. It marks a clear handoff from sellers to buyers over three sessions instead of one.",
    howToSpot: [
      "Appears after a downtrend",
      "Candle 1: a large red body, trend continuing as expected",
      "Candle 2: a small body (bullish or bearish), often near or slightly below candle 1's low — the pause",
      "Candle 3: a large green body closing back above the midpoint of candle 1's body"
    ],
    psychology:
      "The first candle is the downtrend doing its usual thing. The second candle shows sellers running out of steam — the range shrinks and neither side can make real progress. The third candle is buyers seizing that hesitation and driving price back through the entire decline, confirming the sellers have lost control. Three sessions of a visible mood shift carries more weight than a single reversal candle.",
    analogy: {
      title: "The darkest hour before dawn",
      body:
        "A storm rages all night (the big red candle), then the wind dies down to an eerie, uncertain calm just before sunrise (the small middle candle) — it's unclear if the storm will pick back up. Then the sun breaks the horizon and the sky clears fast (the big green candle). The 'morning star' name isn't an accident — it's meant to describe exactly this three-act shift from darkness to light."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.22, high: 0.2, bodyTop: 0.22, bodyBottom: 0.5, low: 0.53, bullish: false },
        { x: 0.5, high: 0.56, bodyTop: 0.6, bodyBottom: 0.65, low: 0.7, bullish: false },
        { x: 0.78, high: 0.24, bodyTop: 0.28, bodyBottom: 0.58, low: 0.62, bullish: true }
      ]
    },
    howToTrade: [
      "Confirm the third candle closes at least back to the midpoint of the first candle's body",
      "Enter at the close of the third candle, or the open of the following candle",
      "Stop loss below the low of the middle (second) candle",
      "Target the next resistance level or a 1:2 reward-to-risk",
      "Stronger when it forms at a known support zone rather than in open air"
    ],
    cheatSheet: { entry: "Close of third candle", stopLoss: "Below the middle candle's low", target: "Next resistance / 1:2 R:R", timeframes: "4H and Daily", bias: "Bullish reversal" },
    mistakes: [
      "Acting after only two candles, before the third confirming candle actually closes",
      "Ignoring a third candle that closes weakly — it needs real strength, not a token bounce",
      "Expecting the exact three-candle shape every time — small variations are normal, the story matters more than the geometry"
    ]
  },
  {
    slug: "evening-star",
    title: "Evening Star",
    category: "candlestick",
    track: "trading",
    accent: "rose",
    bias: "bearish",
    tagline: "Big green candle, a small pause, then a big red candle — dusk falling.",
    whatItIs:
      "The bearish mirror of the morning star: a large up candle, a small-bodied pause candle near the highs, then a large down candle that closes well back into the first candle's range. It's a three-candle handoff from buyers to sellers at the top of a move.",
    howToSpot: [
      "Appears after an uptrend",
      "Candle 1: a large green body, rally continuing as expected",
      "Candle 2: a small body, often near or slightly above candle 1's high — the hesitation",
      "Candle 3: a large red body closing back below the midpoint of candle 1's body"
    ],
    psychology:
      "The rally looks intact through the first candle. The second candle shows the advance stalling — buyers can't extend the range, but sellers haven't taken over yet either. The third candle is sellers finally overwhelming that hesitation and erasing a large chunk of the rally, confirming the top is likely in for now.",
    analogy: {
      title: "Dusk settling in",
      body:
        "The sun is high and bright (the big green candle), then it hovers near the horizon in a brief golden hour where the light is uncertain (the small middle candle) — still day, but clearly changing. Then darkness falls quickly (the big red candle). The pattern's name describes that same steady fade from bright to dark over three stages."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.22, high: 0.16, bodyTop: 0.18, bodyBottom: 0.46, low: 0.5, bullish: true },
        { x: 0.5, high: 0.12, bodyTop: 0.16, bodyBottom: 0.22, low: 0.28, bullish: true },
        { x: 0.78, high: 0.2, bodyTop: 0.24, bodyBottom: 0.58, low: 0.62, bullish: false }
      ]
    },
    howToTrade: [
      "Confirm the third candle closes at least back to the midpoint of the first candle's body",
      "Enter at the close of the third candle, or the open of the following candle",
      "Stop loss above the high of the middle (second) candle",
      "Target the next support level or a 1:2 reward-to-risk",
      "Stronger when it forms at a known resistance zone rather than in open air"
    ],
    cheatSheet: { entry: "Close of third candle", stopLoss: "Above the middle candle's high", target: "Next support / 1:2 R:R", timeframes: "4H and Daily", bias: "Bearish reversal" },
    mistakes: [
      "Selling after only two candles, before the third confirming candle closes",
      "Treating a weak third candle (small body, poor close) the same as a decisive one",
      "Ignoring the broader trend context — this pattern means much more after an extended rally than after a small bounce"
    ]
  },

  // ------------------------------------------------------ SMART MONEY CONCEPTS
  {
    slug: "break-of-structure",
    title: "Break of Structure (BoS)",
    category: "smc",
    track: "trading",
    accent: "violet",
    bias: "bullish",
    tagline: "Price takes out the last swing high or low — the trend confirming itself.",
    whatItIs:
      "A Break of Structure is simply price closing beyond the most recent significant swing point in the direction of the existing trend — a higher high in an uptrend, or a lower low in a downtrend. It's less a standalone pattern and more a confirmation tool: it tells you the trend you think you're in is still intact and still making progress.",
    howToSpot: [
      "Identify the trend's most recent meaningful swing high (uptrend) or swing low (downtrend)",
      "Watch for a full candle close beyond that swing point, not just a brief wick through it",
      "The break should be in the same direction as the prevailing trend",
      "Cleaner on higher timeframes, where swing points are less noisy"
    ],
    psychology:
      "A swing high or low exists because the market previously reversed there — some sellers (at a high) or buyers (at a low) were strong enough to turn price around. When price later closes beyond that same point, it means the side that previously lost that battle has now been overwhelmed, which is meaningful confirmation that the trend still has real force behind it, not just drift.",
    analogy: {
      title: "Beating your own personal best",
      body:
        "A runner keeps beating their own previous best time, race after race. Each new record doesn't just mean they ran fast once — it means whatever limited them last time no longer applies. Each Break of Structure is the market beating its own previous 'record' in the direction it's already heading, proving the trend still has legs."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.82], [0.2, 0.5], [0.36, 0.62], [0.55, 0.3], [0.7, 0.42], [0.9, 0.14]],
      trend1: [[0.55, 0.3], [0.9, 0.3]],
      markers: [{ index: 5, label: "BoS" }]
    },
    howToTrade: [
      "Use BoS to confirm you're trading with the trend, not as a reversal signal",
      "Look to enter on the pullback after the break, toward the broken swing point, rather than chasing the break itself",
      "Stop loss beyond the pullback's low (uptrend) or high (downtrend)",
      "Target the next untested structural level in the direction of the trend",
      "If price breaks structure but immediately reverses hard, treat that as an early warning, not noise"
    ],
    cheatSheet: { entry: "Pullback toward the broken swing point", stopLoss: "Beyond the pullback extreme", target: "Next structural level", timeframes: "1H to Daily", bias: "Trend-confirming, either direction" },
    mistakes: [
      "Treating every wick through a swing point as a confirmed break — insist on a full close",
      "Chasing the breakout candle itself instead of waiting for a lower-risk pullback entry",
      "Using BoS in isolation without asking whether the broader structure even supports a trend"
    ]
  },
  {
    slug: "change-of-character",
    title: "Change of Character (ChoCH)",
    category: "smc",
    track: "trading",
    accent: "amber",
    bias: "bearish",
    tagline: "The trend breaks structure against itself — the first sign it's flipping.",
    whatItIs:
      "A Change of Character is the opposite of a Break of Structure: instead of confirming the existing trend, price closes beyond a swing point against the trend — the first lower low in what had been an uptrend, or the first higher high in what had been a downtrend. It's typically the earliest technical clue that a trend may be ending.",
    howToSpot: [
      "An established trend with a clear sequence of higher highs and higher lows (or the reverse for a downtrend)",
      "A pullback that goes deeper than the previous pullback, breaking a prior swing low in an uptrend (or swing high in a downtrend)",
      "A full candle close beyond that point, not just a wick",
      "Often followed by a period of consolidation as the market decides its new direction"
    ],
    psychology:
      "In a healthy uptrend, every pullback finds buyers before the last higher low. The moment a pullback pushes through that level, it means the buyers who were reliably defending the trend have either stepped back or been overrun — the exact group that had been in control is no longer in control. That's why it's called a change of character: the market is behaving differently than it just was.",
    analogy: {
      title: "A reliable friend missing an appointment",
      body:
        "Someone has shown up on time to the same meeting, same spot, every single week for months — completely reliable. Then one week, without warning, they don't show. It might mean nothing, or it might be the first sign something in their situation has changed. You don't ignore it, but you also don't panic — you just start paying closer attention to what they do next."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.86], [0.18, 0.56], [0.34, 0.66], [0.5, 0.36], [0.64, 0.5], [0.78, 0.6], [1, 0.74]],
      trend1: [[0.34, 0.66], [0.78, 0.66]],
      markers: [{ index: 6, label: "ChoCH" }]
    },
    howToTrade: [
      "Treat ChoCH as a warning to tighten risk on existing trend trades, not necessarily as an instant reversal entry",
      "Wait for the market to also form a new confirming structure (a lower high after the ChoCH, in a former uptrend) before entering the new direction",
      "Stop loss beyond the swing point that most recently formed",
      "Target the level where the original trend's structure began",
      "Be prepared for it to simply lead to a range rather than a full reversal — not every ChoCH becomes a new trend"
    ],
    cheatSheet: { entry: "After a new confirming swing forms", stopLoss: "Beyond the most recent swing point", target: "Start of the prior trend's structure", timeframes: "1H to Daily", bias: "Early reversal warning" },
    mistakes: [
      "Reversing a position immediately on the ChoCH candle itself, before any new structure confirms it",
      "Confusing normal volatility (a slightly deeper pullback) with an actual character change — insist on a real close beyond the swing point",
      "Ignoring the possibility that price simply ranges after the ChoCH instead of reversing outright"
    ]
  },
  {
    slug: "order-block",
    title: "Order Block",
    category: "smc",
    track: "trading",
    accent: "slate",
    bias: "bullish",
    tagline: "The last candle before a sharp move — a footprint of heavy buying or selling.",
    whatItIs:
      "An order block is the last opposing candle (or small cluster of candles) right before price makes a strong, decisive move in the other direction. The idea is that a large amount of buying or selling had to be absorbed at that specific spot to fuel the move that followed — so if price returns there later, that same zone may attract similar interest again.",
    howToSpot: [
      "A strong, fast move in one direction — a clear impulse, not a slow grind",
      "Look at the last candle (or few candles) moving the opposite way immediately before that impulse began",
      "That candle's range becomes the order block zone",
      "The stronger and faster the impulse that followed, the more significant the zone is treated as being"
    ],
    psychology:
      "A sharp move doesn't happen without real size behind it. If a strong rally starts right after one last red candle, that red candle is where a lot of selling got absorbed by buyers before they took full control — the zone marks where a real supply-and-demand imbalance occurred. Price often returns to retest that exact zone before continuing, because it represents a level where large participants were previously active.",
    analogy: {
      title: "The last footprint before a sprint",
      body:
        "Imagine looking at footprints in the sand and finding the exact spot where someone's stride suddenly lengthens from a walk into a full sprint. That one footprint, right at the transition, tells you something changed there — a push-off point. The order block is that push-off point on the price chart: the last mark left before the move accelerated."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.8], [0.18, 0.7], [0.3, 0.74], [0.42, 0.68], [0.62, 0.28], [0.82, 0.5], [1, 0.2]],
      zone: { x1: 0.24, x2: 0.42, yTop: 0.6, yBottom: 0.76, label: "Order Block" }
    },
    howToTrade: [
      "Mark the order block zone once a strong impulse move has already happened — this is identified after the fact, not predicted in advance",
      "Wait for price to pull back into that zone rather than chasing the original move",
      "Enter in the direction of the original impulse on a reaction from the zone",
      "Stop loss just beyond the far edge of the order block",
      "Target the high (or low) of the original impulse move, or the next structural level beyond it"
    ],
    cheatSheet: { entry: "Reaction from the order block zone", stopLoss: "Beyond the zone's far edge", target: "Prior impulse extreme or next structure level", timeframes: "15M to 4H", bias: "Directional, matches the original impulse" },
    mistakes: [
      "Marking every candle before any move as an order block — reserve it for genuinely sharp, decisive impulses",
      "Assuming a zone will hold forever — once price has closed well beyond it, its usefulness fades",
      "Trading the zone in isolation without checking whether the broader trend agrees with the trade direction"
    ]
  },
  {
    slug: "fair-value-gap",
    title: "Fair Value Gap (Imbalance)",
    category: "smc",
    track: "trading",
    accent: "lime",
    bias: "bullish",
    tagline: "A three-candle gap the market skipped over — often revisited later.",
    whatItIs:
      "A Fair Value Gap (also called an imbalance) happens across three consecutive candles when the move is so fast that the high of the first candle and the low of the third candle don't overlap, leaving an untraded price gap in between. Because that zone was skipped rather than traded through normally, price often comes back to 'fill' it before continuing on its way.",
    howToSpot: [
      "Look at any three consecutive candles during a strong, fast move",
      "Check whether candle one's high sits below candle three's low (bullish gap) or candle one's low sits above candle three's high (bearish gap)",
      "The empty space between those two points is the fair value gap",
      "Larger, cleaner gaps on higher timeframes tend to matter more than tiny ones on 1-minute charts"
    ],
    psychology:
      "Price normally moves through a level with plenty of two-way trading — some buyers, some sellers, all agreeing on value along the way. When a move is aggressive enough to leave a gap, it means one side was so dominant that almost no one got to transact at those in-between prices. Markets tend to be inefficient with untraded space like that, and often drift back to let late participants trade at those levels before resuming the original direction.",
    analogy: {
      title: "Skipping a step on the stairs",
      body:
        "Someone runs up a staircase so fast they skip a step entirely. They don't fall — they're clearly capable and moving forward with purpose — but that one step was never actually touched. Later, whether it's them coming back down or someone else going up, that missed step often gets stepped on eventually. It's simply still there, waiting to be used."
    },
    diagram: {
      kind: "candles",
      candles: [
        { x: 0.25, high: 0.6, bodyTop: 0.62, bodyBottom: 0.78, low: 0.8, bullish: false },
        { x: 0.5, high: 0.2, bodyTop: 0.22, bodyBottom: 0.55, low: 0.58, bullish: true },
        { x: 0.75, high: 0.12, bodyTop: 0.14, bodyBottom: 0.3, low: 0.33, bullish: true }
      ],
      zone: { x1: 0.1, x2: 0.9, yTop: 0.33, yBottom: 0.6, label: "Fair Value Gap" }
    },
    howToTrade: [
      "Identify the gap after the three-candle sequence completes",
      "Wait for price to pull back into the gap rather than trading the original impulse candle itself",
      "Enter in the direction of the original move once price reacts inside the gap",
      "Stop loss just beyond the far edge of the gap",
      "Target the high (or low) of the original impulse, or the next fair value gap beyond it"
    ],
    cheatSheet: { entry: "Reaction inside the gap on a pullback", stopLoss: "Beyond the far edge of the gap", target: "Prior impulse extreme / next gap", timeframes: "15M to 4H", bias: "Directional, matches the original move" },
    mistakes: [
      "Assuming every gap gets filled immediately — some take a while, and some get filled only partially before continuing",
      "Ignoring the overall trend and trading every gap as an equal opportunity regardless of direction",
      "Treating a tiny, barely-there gap on a low timeframe the same as a large, clean one on a higher timeframe"
    ]
  },
  {
    slug: "liquidity-grab",
    title: "Liquidity Grab",
    category: "smc",
    track: "trading",
    accent: "slate",
    bias: "bearish",
    tagline: "A sharp poke above a high (or below a low) — then a fast reversal.",
    whatItIs:
      "A liquidity grab happens when price pushes briefly beyond an obvious high or low — the kind that's likely to have stop-loss and breakout orders resting just beyond it — then reverses sharply right after. The move isn't really about breaking out; it's about triggering the orders sitting at that level before the real move happens in the other direction.",
    howToSpot: [
      "An obvious, well-known swing high or low that many traders would logically place stops beyond",
      "A quick spike beyond that level, often on a long wick rather than a strong close",
      "A fast reversal back through the level shortly after the spike",
      "Frequently happens right before major news or session opens, when liquidity is thin and spikes travel further"
    ],
    psychology:
      "Obvious levels attract obvious orders — breakout buyers above a well-known high, stop-loss sellers below a well-known low, and vice versa. A brief spike through that level fills all of those resting orders, providing the exact liquidity needed for larger participants to enter a position in the opposite direction at a good price. Once those orders are used up, there's often little left to sustain the spike, and price snaps back.",
    analogy: {
      title: "A fire drill that's actually a drill",
      body:
        "An alarm goes off and everyone rushes for the exits, assuming the worst. Moments later it turns out to be a test — a false alarm designed to see who reacts. The brief poke above a high or below a low works the same way: it looks like the real move, gets everyone to react (place orders, get stopped out), and then reveals itself as the setup for the actual move rather than the move itself."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.7], [0.2, 0.42], [0.4, 0.5], [0.58, 0.3], [0.68, 0.1], [0.8, 0.46], [1, 0.68]],
      trend1: [[0, 0.3], [0.68, 0.3]],
      markers: [{ index: 4, label: "Grab" }, { index: 6, label: "Reversal" }]
    },
    howToTrade: [
      "Don't chase the spike itself — that's the trap the pattern is named for",
      "Wait for price to close back inside the prior range after the spike",
      "Enter in the direction opposite the spike once that reversal close confirms",
      "Stop loss just beyond the extreme of the spike",
      "Target the opposite side of the recent range, or the next meaningful structural level"
    ],
    cheatSheet: { entry: "Confirmed close back inside the range", stopLoss: "Beyond the spike's extreme", target: "Opposite side of the range", timeframes: "5M to 4H", bias: "Reversal, opposite the spike" },
    mistakes: [
      "Placing stops at the most obvious, round-number level everyone else is watching — that's exactly where grabs happen",
      "Trading the spike direction itself, assuming it's a genuine breakout",
      "Entering the reversal before price actually confirms with a close back inside the range"
    ]
  }  ,
  // ------------------------------------------------------------ CRYPTO
  {
    slug: "market-cap-vs-price",
    title: "Market Cap vs. Coin Price",
    category: "crypto-fundamentals",
    track: "crypto",
    accent: "cyan",
    bias: "neutral",
    tagline: "A cheap-looking coin price tells you almost nothing on its own.",
    whatItIs:
      "A coin's price is just total value divided by however many coins exist — it says nothing about the size of the project. Market cap (price × circulating supply) is the number that actually tells you how big something is. A coin priced at $0.001 with 500 billion coins can be a bigger project than a $50,000 coin with only 20 million in existence.",
    howToSpot: [
      "Market cap = current price × circulating supply, not total/max supply",
      "Two assets can have wildly different prices and the same market cap",
      "A 'cheap' price with an enormous supply is not automatically a bargain",
      "Compare market caps to judge relative size, never raw prices",
      "Circulating supply can change over time as coins unlock or get burned"
    ],
    psychology:
      "A low price per coin feels psychologically like more upside — '100x easier to 10x from $0.001 than from $50,000' — but that logic ignores supply entirely. Traders chasing cheap-looking coins are really just buying more units of the same total value, which is why so many low-price coins with huge supplies underperform coins that already look 'expensive' per unit.",
    analogy: {
      title: "Slices vs. the size of the pizza",
      body:
        "A large pizza cut into 1,000 tiny slices and a small pizza cut into 4 big slices can cost the same per slice by coincidence, but that tells you nothing about which pizza is actually bigger. Market cap is the size of the whole pizza. Price per coin is just the size of one slice — and slice count is arbitrary."
    },
    diagram: {
      kind: "bars",
      bars: [
        { label: "Coin A\n$2 · 10B supply", value: 1.0, display: "$20B cap" },
        { label: "Coin B\n$40,000 · 250K supply", value: 0.5, display: "$10B cap" }
      ]
    },
    howToTrade: [
      "Before buying anything because it 'looks cheap', check its market cap against projects you already understand",
      "Rank a shortlist of coins by market cap, not by price, before deciding where size-adjusted upside actually sits",
      "Watch circulating supply changes (unlocks, vesting cliffs) — a jump in supply can quietly dilute your share even if price holds",
      "Use market cap to size position risk sensibly: a $50M-cap coin is far more volatile than a $500B-cap one"
    ],
    cheatSheet: {
      entry: "Rank candidates by market cap, not sticker price",
      stopLoss: "Cheap price + huge/inflating supply, with no real usage behind it",
      target: "Pairs well with circulating-vs-total-supply and on-chain activity",
      timeframes: "Recheck whenever a supply unlock or burn event is scheduled",
      bias: "Sizing signal, not a buy/sell trigger on its own"
    },
    mistakes: [
      "Assuming a lower price per coin means more room to grow",
      "Comparing prices across two different coins as if that means anything",
      "Ignoring scheduled token unlocks that will expand circulating supply later"
    ]
  },
  {
    slug: "circulating-vs-total-supply",
    title: "Circulating vs. Total Supply",
    category: "crypto-fundamentals",
    track: "crypto",
    accent: "violet",
    bias: "neutral",
    tagline: "The coins in the market today are rarely all the coins that will ever exist.",
    whatItIs:
      "Circulating supply is what's actually tradable right now. Total supply includes coins that exist but are locked, vested, or not yet released — think team allocations, treasury reserves, or staking rewards still to be unlocked. Max supply is the hard ceiling a protocol will ever mint, if it has one at all.",
    howToSpot: [
      "Circulating supply: what's freely tradable today",
      "Total supply: circulating + everything minted but locked, reserved, or unvested",
      "Max supply: the absolute cap the protocol will ever issue (some coins have no cap)",
      "A big gap between circulating and total supply signals future dilution ahead",
      "Vesting schedules and unlock calendars are usually published by the project itself"
    ],
    psychology:
      "A small circulating supply relative to total supply can make demand look stronger than it really is, because there simply isn't much available to sell yet. When a large unlock hits and early holders (team, VCs) can finally sell, that hidden supply meets the market all at once — often catching latecomers who never looked past today's circulating number.",
    analogy: {
      title: "The full warehouse behind the storefront",
      body:
        "Circulating supply is what's on the shelves for sale today. Total supply is everything in the warehouse out back, some of it contractually locked until a future date. If you only look at what's on the shelves, you'll misjudge how much more could show up the day that warehouse door opens."
    },
    diagram: {
      kind: "donut",
      segments: [
        { label: "Circulating (tradable now)", value: 0.55, color: "cyan" },
        { label: "Locked / vesting", value: 0.30, color: "amber" },
        { label: "Team & treasury reserve", value: 0.15, color: "slate" }
      ]
    },
    howToTrade: [
      "Before buying, find the project's unlock schedule and mark the next big date on your calendar",
      "Treat a large upcoming unlock as a known supply-side headwind, not a surprise",
      "Prefer projects with transparent, gradual vesting over cliff unlocks that dump supply all at once",
      "Weigh circulating supply against real usage — a small float with real demand behaves very differently from a small float with none"
    ],
    cheatSheet: {
      entry: "Check the unlock calendar before entering a position",
      stopLoss: "A cliff unlock landing right around your planned exit window",
      target: "Pairs well with market-cap-vs-price for full supply context",
      timeframes: "Recheck monthly, and always right before a scheduled unlock",
      bias: "Supply-side risk factor, not a standalone signal"
    },
    mistakes: [
      "Only ever checking circulating supply and never total or max supply",
      "Getting caught by a scheduled unlock that was public information the whole time",
      "Confusing 'no max supply' with 'infinite dilution risk' — check actual issuance rate, not just the absence of a cap"
    ]
  },
  {
    slug: "halving-and-scarcity",
    title: "Halving Cycles and Scarcity",
    category: "crypto-fundamentals",
    track: "crypto",
    accent: "amber",
    bias: "neutral",
    tagline: "Some protocols cut new supply in half on a fixed schedule, on purpose.",
    whatItIs:
      "A halving (or similar scheduled supply cut) is a hard-coded, predictable reduction in how many new coins are created per block. It doesn't touch existing supply — it slows down how fast new supply gets added going forward, which is why it's discussed as a scarcity event rather than a price-moving switch by itself.",
    howToSpot: [
      "The event date and mechanism are usually written into the protocol's code, not decided by anyone",
      "New-coin issuance rate drops sharply (commonly by half) at the event",
      "Existing circulating supply is unaffected — only the pace of future issuance changes",
      "Halvings are announced and counted down publicly well in advance",
      "Miner/validator revenue from new issuance drops at the same moment, which matters for network security economics"
    ],
    psychology:
      "Because the schedule is public and predictable, much of the anticipated scarcity gets priced in gradually beforehand rather than arriving as a shock on the day itself. The common mistake is treating the halving date as a guaranteed price-pump trigger — history shows reactions vary a lot, and demand still has to show up for scarcity to matter at all.",
    analogy: {
      title: "The tap gets turned down, not the pool drained",
      body:
        "Think of new supply as water flowing from a tap into a pool. A halving turns that tap down to half its flow rate — it doesn't remove a drop of water already in the pool. Whether the pool's water becomes more valuable depends entirely on whether people still want to keep filling their cups from it."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.15], [0.24, 0.15], [0.25, 0.5], [0.49, 0.5], [0.5, 0.72], [0.74, 0.72], [0.75, 0.85], [1, 0.85]],
      markers: [
        { index: 2, label: "1st halving" },
        { index: 4, label: "2nd halving" },
        { index: 6, label: "3rd halving" }
      ]
    },
    howToTrade: [
      "Mark the next scheduled event date and treat it as a known catalyst, not a surprise",
      "Watch issuance-rate and miner/validator economics around the event, not just spot price",
      "Don't assume the reaction repeats the same way every cycle — check what's different this time (demand backdrop, macro conditions)",
      "Zoom out: scarcity events play out over months, not the single day of the halving itself"
    ],
    cheatSheet: {
      entry: "Track the countdown, position ahead of the crowd rushing in at the deadline",
      stopLoss: "Assuming the date itself guarantees a pump",
      target: "Pairs well with circulating-vs-total-supply for the full scarcity picture",
      timeframes: "Multi-month view around the event, not intraday",
      bias: "Long-run scarcity factor, not a short-term trade signal"
    },
    mistakes: [
      "Buying purely because a halving is coming, with no view on actual demand",
      "Expecting the exact same price pattern as the previous cycle",
      "Forgetting that reduced issuance can also mean reduced network security budget for proof-of-work chains"
    ]
  },
  {
    slug: "funding-rate-and-perpetuals",
    title: "Funding Rate on Perpetual Futures",
    category: "crypto-mechanics",
    track: "crypto",
    accent: "rose",
    bias: "neutral",
    tagline: "A recurring fee that keeps a never-expiring futures contract tied to spot.",
    whatItIs:
      "A perpetual futures contract never expires, so exchanges use a periodic funding payment between longs and shorts to keep its price anchored to the real spot price. When the perpetual trades above spot, longs pay shorts (positive funding); when it trades below spot, shorts pay longs (negative funding).",
    howToSpot: [
      "Funding is paid directly between traders, not to or from the exchange itself",
      "Positive funding: perpetual price > spot, longs are paying, sentiment is crowded bullish",
      "Negative funding: perpetual price < spot, shorts are paying, sentiment is crowded bearish",
      "Funding is charged on a fixed interval (commonly every 8 hours) and shown as an annualized-looking percentage",
      "Extreme funding in either direction usually means one side of the trade is overcrowded"
    ],
    psychology:
      "Very high positive funding means an unusually large number of traders are leveraged long and paying for the privilege — that crowding is exactly the fuel for a sharp downside flush if price stalls and those longs get squeezed out. The reverse is true for deeply negative funding and short squeezes. Funding is essentially a live read of how one-sided leveraged positioning has become.",
    analogy: {
      title: "A toll for tilting the seesaw",
      body:
        "Picture longs and shorts on a seesaw that's supposed to sit level with the spot price. When too many people pile onto the 'long' side and tilt it up, they pay a toll to the shorts holding the other end down — a small, constant nudge trying to bring the seesaw back level."
    },
    diagram: {
      kind: "diverging-bars",
      bars: [
        { label: "Calm market", value: 0.15, display: "+0.01%" },
        { label: "Crowded longs", value: 0.85, display: "+0.09%" },
        { label: "Crowded shorts", value: -0.7, display: "-0.07%" }
      ]
    },
    howToTrade: [
      "Treat extreme positive funding as a warning that longs are crowded, not as a reason to short blindly",
      "Treat extreme negative funding the same way in reverse for shorts",
      "Combine funding with open interest — rising open interest plus extreme funding is the classic squeeze setup",
      "Never ignore funding cost on a position you plan to hold across multiple funding intervals; it compounds"
    ],
    cheatSheet: {
      entry: "Read funding as a crowding gauge before adding to a leveraged position",
      stopLoss: "Holding a heavily-funded position through several payment intervals without accounting for the cost",
      target: "Pairs well with open interest for spotting squeeze setups",
      timeframes: "Checked every funding interval (commonly every 8 hours)",
      bias: "Sentiment/crowding gauge, not a directional signal by itself"
    },
    mistakes: [
      "Confusing funding rate with a trading fee owed to the exchange",
      "Opening a large leveraged position without checking current funding cost first",
      "Assuming extreme funding means an immediate reversal — crowded trades can stay crowded longer than expected"
    ]
  },
  {
    slug: "stablecoins-and-pegs",
    title: "Stablecoins and the Peg",
    category: "crypto-mechanics",
    track: "crypto",
    accent: "emerald",
    bias: "neutral",
    tagline: "Designed to hold a fixed value — but 'designed to' isn't a guarantee.",
    whatItIs:
      "A stablecoin is a crypto asset built to track a stable reference, almost always $1 USD. It holds that peg through backing reserves (cash/short-term assets), over-collateralized crypto collateral, or an algorithmic mechanism — and each approach has different failure modes if trust or liquidity breaks down.",
    howToSpot: [
      "Fiat-backed: reserves of cash/cash-equivalents meant to match tokens 1:1, redeemable through the issuer",
      "Crypto-collateralized: over-collateralized by other crypto assets, managed by smart contracts",
      "Algorithmic: relies on incentive mechanisms and arbitrage rather than hard reserves to hold the peg",
      "A healthy stablecoin trades within a tight band around its $1 target almost all the time",
      "A sustained gap from $1 (a 'depeg') signals the market doubts the backing or mechanism"
    ],
    psychology:
      "Confidence is the actual peg mechanism for most designs — reserves and smart contracts only work if enough people believe redemption will be honored. The moment that belief cracks, even briefly, holders can rush to exit at once, which is exactly the kind of run that turns a small wobble into a real depeg.",
    analogy: {
      title: "A promise to always exchange $1 for $1",
      body:
        "A stablecoin is like a claim ticket that says 'redeemable for exactly $1, always.' As long as everyone trusts that ticket and doesn't all show up to cash it in on the same day, it trades like a dollar. Trust is the actual peg — the reserves are just what backs the promise up if trust is ever tested."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.5], [0.2, 0.48], [0.4, 0.52], [0.55, 0.5], [0.65, 0.8], [0.78, 0.55], [1, 0.5]],
      trend1: [[0, 0.5], [1, 0.5]],
      markers: [{ index: 4, label: "Depeg event" }]
    },
    howToTrade: [
      "Know which mechanism a stablecoin uses before holding it in size, not after something goes wrong",
      "Watch for a sustained (not momentary) gap from $1 — brief wobbles happen even in healthy stablecoins",
      "Diversify stablecoin exposure across issuers rather than parking everything with one",
      "Understand your actual redemption path (who can redeem, at what minimum size, how fast) before you need it"
    ],
    cheatSheet: {
      entry: "Check backing/audits before parking meaningful size in any stablecoin",
      stopLoss: "A sustained trade away from $1 with no clear resolution timeline",
      target: "Pairs well with on-chain-vs-exchange-custody for storage risk",
      timeframes: "Recheck reserve reports/attestations periodically, not just once",
      bias: "Risk-management topic, not a trading signal"
    },
    mistakes: [
      "Treating every stablecoin as equally 'safe' regardless of its backing mechanism",
      "Ignoring a slow, sustained depeg because it hasn't made headlines yet",
      "Holding a very large stablecoin position with no idea how redemption actually works"
    ]
  },
  {
    slug: "on-chain-vs-exchange-custody",
    title: "Self-Custody vs. Exchange Custody",
    category: "crypto-mechanics",
    track: "crypto",
    accent: "slate",
    bias: "neutral",
    tagline: "Whoever holds the private keys is the one who actually controls the coins.",
    whatItIs:
      "Self-custody means you hold your own private keys (in a wallet you control), so only you can move your coins. Exchange custody means the exchange holds the keys on your behalf, and your account shows a balance they owe you — a claim against them, not coins in your own wallet, until you withdraw.",
    howToSpot: [
      "Self-custody: you personally control the private key or seed phrase",
      "Exchange custody: the exchange's own wallets hold the actual coins; your app balance is a ledger entry",
      "You can verify self-custodied holdings directly on-chain at any time",
      "Exchange balances depend entirely on that exchange staying solvent and honest",
      "Withdrawing from an exchange converts a custodial claim into a self-custodied holding"
    ],
    psychology:
      "Leaving coins on an exchange feels convenient and 'safe' because the interface looks identical to genuine ownership — but it quietly shifts the actual holding risk onto a third party's solvency and security. Most people only think about that distinction after an exchange gets hacked or halts withdrawals, which is exactly the wrong time to learn it.",
    analogy: {
      title: "Cash under your mattress vs. cash in someone else's safe",
      body:
        "Self-custody is cash under your own mattress — nobody else can touch it, but you're also fully responsible for not losing it. Exchange custody is cash in someone else's safe with your name on a receipt. Convenient, and usually fine, but it's still their safe, their rules, and their solvency standing between you and your money."
    },
    diagram: {
      kind: "donut",
      segments: [
        { label: "Self-custody (your wallet)", value: 0.5, color: "cyan" },
        { label: "Exchange custody (their wallet)", value: 0.5, color: "slate" }
      ]
    },
    howToTrade: [
      "Keep active trading capital on the exchange you're actively using; move long-term holdings to self-custody",
      "Never treat an exchange balance as equivalent to coins already in your own wallet",
      "Understand withdrawal limits and processing times before you need to move funds urgently",
      "Split large holdings across custody methods rather than concentrating everything in one place"
    ],
    cheatSheet: {
      entry: "Move long-term holdings to self-custody once trading activity is done",
      stopLoss: "Leaving your entire holdings on one exchange indefinitely with no plan",
      target: "Pairs well with stablecoins-and-pegs for overall storage risk",
      timeframes: "Review your custody split periodically, not just once",
      bias: "Risk-management topic, not a trading signal"
    },
    mistakes: [
      "Assuming an exchange balance is exactly as safe as a self-custodied wallet",
      "Never testing a small withdrawal to confirm the process actually works before you need it at scale",
      "Losing a self-custody seed phrase — with self-custody, there's no support line to call"
    ]
  },
  // ------------------------------------------------------------ STOCKS
  {
    slug: "pe-ratio-explained",
    title: "P/E Ratio Explained",
    category: "stock-fundamentals",
    track: "stocks",
    accent: "violet",
    bias: "neutral",
    tagline: "How many years of current profit you're paying for, in one number.",
    whatItIs:
      "The price-to-earnings ratio divides a company's share price by its earnings per share. A P/E of 20 means investors are paying 20 times last year's (or next year's, for forward P/E) earnings for one share — roughly, 20 years of current profit to 'earn back' the price, if nothing ever changed.",
    howToSpot: [
      "Trailing P/E uses the last 12 months of reported earnings",
      "Forward P/E uses analysts' earnings estimates for the year ahead",
      "A high P/E usually means the market expects fast future growth",
      "A low P/E can mean genuine undervaluation — or a real problem the market has already priced in",
      "P/E only makes sense compared against something: the sector average, peers, or the company's own history"
    ],
    psychology:
      "A 'cheap' low P/E stock feels safe and a 'pricey' high P/E stock feels risky, but P/E alone doesn't say which is true — it just reflects what the market currently expects. Growth companies often run high P/Es for years specifically because earnings keep growing into that price, while some low-P/E stocks stay cheap because the market rightly expects earnings to shrink.",
    analogy: {
      title: "The payback period on a rental property",
      body:
        "P/E is like judging a rental property by how many years of current rent it would take to pay back the purchase price. A property charging more per year needs fewer years to pay back — that's a 'lower P/E'. But you'd still want to know if the rent is about to rise, fall, or stay flat before deciding it's a good deal."
    },
    diagram: {
      kind: "bars",
      bars: [
        { label: "Stock A", value: 0.45, display: "12x" },
        { label: "Stock B", value: 0.9, display: "24x" },
        { label: "Stock C", value: 0.68, display: "18x" }
      ],
      trend1: [[0, 0.32], [1, 0.32]]
    },
    howToTrade: [
      "Always compare a stock's P/E to its own sector average, not to the market as a whole",
      "Pair P/E with the earnings growth rate — a high P/E next to fast growth (a low PEG ratio) can still be reasonable",
      "Be suspicious of a very low P/E with declining earnings — it may be 'cheap' for a real reason",
      "Recheck P/E every earnings season since the 'E' in the ratio changes with each report"
    ],
    cheatSheet: {
      entry: "Compare to the sector average, not in isolation",
      stopLoss: "A P/E far above peers with no growth story to justify it",
      target: "Pairs well with P/B ratio, PEG ratio, and the earnings growth trend",
      timeframes: "Recheck each earnings season as the 'E' updates",
      bias: "Valuation signal, not a standalone buy/sell trigger"
    },
    mistakes: [
      "Judging a stock as 'cheap' or 'expensive' from P/E alone, with no peer comparison",
      "Ignoring that a very high or very low P/E is usually pricing in a specific future expectation",
      "Comparing P/E across totally different sectors, where 'normal' ranges vary a lot"
    ]
  },
  {
    slug: "market-cap-and-free-float",
    title: "Market Cap and Free Float",
    category: "stock-fundamentals",
    track: "stocks",
    accent: "cyan",
    bias: "neutral",
    tagline: "Not every issued share is actually available for you to trade.",
    whatItIs:
      "Market cap is share price times total shares outstanding — the company's total equity value. Free float is the subset of those shares actually available to trade publicly, excluding shares locked up by insiders, founders, or governments. A small free float relative to market cap means fewer shares chasing the same demand, which can make price swings sharper in both directions.",
    howToSpot: [
      "Market cap = share price × total shares outstanding",
      "Free float = shares outstanding minus insider/founder/strategic locked holdings",
      "A low free-float percentage means the tradable slice of the company is much smaller than its headline market cap",
      "Index providers often weight companies by free-float market cap, not total market cap",
      "Free float can change over time as lock-ups expire or insiders sell down stakes"
    ],
    psychology:
      "A stock with a huge market cap but a tiny free float can move violently on modest trading volume, because there simply isn't much supply available to absorb buying or selling pressure. Traders who size positions off the headline market cap alone can badly misjudge how illiquid the actual tradable float really is.",
    analogy: {
      title: "The whole building vs. the units for rent",
      body:
        "Market cap is the value of an entire apartment building. Free float is only the units actually listed for rent, not the ones the owner lives in or keeps off the market. A tiny handful of available units can swing the 'going rate' far more than the size of the whole building would suggest."
    },
    diagram: {
      kind: "donut",
      segments: [
        { label: "Free float (tradable)", value: 0.35, color: "cyan" },
        { label: "Insider / founder holdings", value: 0.45, color: "slate" },
        { label: "Strategic / government stake", value: 0.20, color: "amber" }
      ]
    },
    howToTrade: [
      "Check free-float percentage, not just market cap, before sizing a position in a smaller or newly-listed company",
      "Expect sharper moves on lower-float stocks around news, earnings, or lock-up expiry dates",
      "Watch upcoming lock-up expiries — a flood of newly-tradable shares can pressure price",
      "Use average daily volume alongside float to judge how easily you could exit a position"
    ],
    cheatSheet: {
      entry: "Check float percentage before sizing a position in smaller names",
      stopLoss: "A lock-up expiry landing right around your planned exit window",
      target: "Pairs well with average daily volume for a liquidity read",
      timeframes: "Recheck around scheduled lock-up expiry dates",
      bias: "Liquidity/risk-sizing factor, not a directional signal"
    },
    mistakes: [
      "Sizing a position off total market cap while ignoring how small the free float actually is",
      "Being surprised by a lock-up expiry that was public knowledge from the IPO prospectus",
      "Assuming a 'big company' is automatically liquid — float size, not market cap, drives real tradability"
    ]
  },
  {
    slug: "earnings-season-and-eps",
    title: "Earnings Season and EPS Surprises",
    category: "stock-events",
    track: "stocks",
    accent: "amber",
    bias: "neutral",
    tagline: "The market reacts to the gap between expected and actual, not the number alone.",
    whatItIs:
      "Earnings per share (EPS) is a company's profit divided by its shares outstanding. Once a quarter, companies report actual results against what analysts collectively expected — a 'beat' means actual EPS came in above estimates, a 'miss' means it came in below. Price often reacts more to that surprise (and forward guidance) than to whether profit grew at all.",
    howToSpot: [
      "The consensus estimate is the average of professional analysts' EPS forecasts before the report",
      "A 'beat' or 'miss' is measured against that consensus, not against last year's number",
      "Forward guidance (management's outlook for the next quarter/year) often moves price more than the past quarter's actual result",
      "Revenue beats/misses are reported alongside EPS and can tell a different story than earnings alone",
      "After-hours/pre-market price moves right after a report reflect the market digesting the surprise in real time"
    ],
    psychology:
      "A company can grow profit year-over-year and still see its stock fall hard, simply because it grew less than the market had already priced in. That's the core of why 'beat expectations' matters more short-term than the raw growth number — the market had already bet on a certain outcome, and the surprise relative to that bet is what gets traded.",
    analogy: {
      title: "Beating your own prediction, not last year's score",
      body:
        "Imagine you predicted you'd run a race in 20 minutes, and everyone bet based on that. If you actually finish in 19 minutes, that's a 'beat' even though it's a similar time to last year — because you did better than the specific number people were expecting. Finish in 21 minutes and it's a 'miss', even if that's still faster than you ran two years ago."
    },
    diagram: {
      kind: "diverging-bars",
      bars: [
        { label: "Q1: beat", value: 0.6, display: "+8%" },
        { label: "Q2: miss", value: -0.35, display: "-4%" },
        { label: "Q3: beat", value: 0.8, display: "+11%" },
        { label: "Q4: in-line", value: 0.05, display: "+0.5%" }
      ]
    },
    howToTrade: [
      "Check consensus estimates before the report, not just the prior quarter's actual number",
      "Weigh forward guidance at least as heavily as the reported quarter itself",
      "Separate EPS beats/misses from revenue beats/misses — they can tell conflicting stories",
      "Be cautious trading directly into an earnings report; the initial reaction can reverse once the full call is digested"
    ],
    cheatSheet: {
      entry: "Compare actual EPS/revenue to consensus, and weigh forward guidance",
      stopLoss: "Trading size purely on last year's number, ignoring what was actually expected",
      target: "Pairs well with P/E ratio for the valuation reaction afterward",
      timeframes: "Quarterly, around each scheduled earnings date",
      bias: "Event-driven volatility, not a standing directional signal"
    },
    mistakes: [
      "Reacting to 'profit grew' headlines without checking whether it beat or missed consensus",
      "Ignoring forward guidance, which often matters more than the quarter just reported",
      "Holding a large position through an earnings date without sizing for the extra volatility"
    ]
  },
  {
    slug: "dividends-and-yield",
    title: "Dividends and Dividend Yield",
    category: "stock-fundamentals",
    track: "stocks",
    accent: "emerald",
    bias: "neutral",
    tagline: "A cash return to shareholders, expressed as a percentage of share price.",
    whatItIs:
      "A dividend is a portion of profit a company pays directly to shareholders, usually quarterly. Dividend yield is the annual dividend per share divided by the current share price, expressed as a percentage — it tells you the cash return relative to what you'd pay for the stock today, separate from any price appreciation.",
    howToSpot: [
      "Dividend yield = annual dividend per share ÷ current share price",
      "Yield rises automatically if price falls and the dividend stays the same — and vice versa",
      "The payout ratio (dividend ÷ earnings) shows how much of profit is being paid out versus retained",
      "A dividend can be cut or suspended; past payments are not a guarantee of future ones",
      "Ex-dividend date matters: buying after it means you won't receive that specific upcoming payment"
    ],
    psychology:
      "An unusually high yield can look like a great deal, but yield rises when price falls — so a very high number is sometimes the market pricing in a dividend cut that hasn't been announced yet, not a genuine bargain. Chasing the highest yield on a list without checking payout ratio and earnings trend is a classic way to walk into exactly that trap.",
    analogy: {
      title: "Rental yield on a property that might need repairs",
      body:
        "Dividend yield is like rental yield on a property — the cash return relative to price. But if a landlord is paying out far more in rent than the property realistically earns, that's not a great sign for how long the payments can continue. The same caution applies to a company paying out more than its earnings comfortably support."
    },
    diagram: {
      kind: "bars",
      bars: [
        { label: "Stock A", value: 0.4, display: "2.1%" },
        { label: "Stock B", value: 0.7, display: "3.8%" },
        { label: "Stock C", value: 1.0, display: "5.4%" }
      ]
    },
    howToTrade: [
      "Check payout ratio alongside yield — a yield paid from a small, sustainable slice of earnings is healthier than one paid from nearly all of it",
      "Look at the dividend growth history, not just today's snapshot yield",
      "Treat an unusually high yield versus peers as a flag to investigate, not automatically a bargain",
      "Note the ex-dividend date if timing a purchase specifically to capture a payment"
    ],
    cheatSheet: {
      entry: "Check payout ratio and dividend history before chasing a high yield",
      stopLoss: "A yield far above sector peers with no clear explanation",
      target: "Pairs well with P/E ratio and earnings trend for the full income picture",
      timeframes: "Recheck each time a dividend is declared or earnings are reported",
      bias: "Income/valuation factor, not a standalone signal"
    },
    mistakes: [
      "Buying the highest-yielding stock on a screener without checking if the payout is sustainable",
      "Forgetting that a falling share price alone can inflate yield with no real improvement underneath",
      "Ignoring the ex-dividend date and being surprised by a small price adjustment around it"
    ]
  },
  {
    slug: "ipo-lifecycle",
    title: "The IPO Lifecycle",
    category: "stock-events",
    track: "stocks",
    accent: "rose",
    bias: "neutral",
    tagline: "From private company to public stock — and the lock-up cliff most new investors forget.",
    whatItIs:
      "An IPO (initial public offering) is when a private company sells shares to the public for the first time. The path typically runs from the priced offering, to a first-day 'pop' (or drop) as public trading begins, through a quiet period, and into a lock-up expiry date when early insiders and investors are finally allowed to sell — a moment that can bring a wave of new selling pressure.",
    howToSpot: [
      "Offer price: the price set before the stock starts public trading",
      "First-day pop: the often-volatile jump (or fall) once trading opens",
      "Lock-up period: typically 90-180 days where insiders/early investors cannot sell",
      "Lock-up expiry: the date that restriction lifts, often watched closely for a supply-driven dip",
      "Quiet period: a window with restrictions on company promotional communication right after listing"
    ],
    psychology:
      "The first-day pop gets all the headlines, but a huge slice of a newly-public company's actual shares are still locked up and simply can't be sold yet — which means the stock's early trading behavior reflects a small floating supply, not the company's true long-run supply-demand balance. Many investors get excited by a strong debut without realizing the real test comes months later at lock-up expiry.",
    analogy: {
      title: "A grand opening with most of the inventory still in the back room",
      body:
        "An IPO's first trading day is like a store's grand opening where only a fraction of total inventory is actually out on the shelves — the rest is contractually stuck in the back room for months. Early demand can look dramatic against that thin available supply. The real supply-demand picture only shows up once everything in the back room is allowed onto the shelves."
    },
    diagram: {
      kind: "line",
      points: [[0, 0.75], [0.15, 0.2], [0.3, 0.3], [0.5, 0.25], [0.65, 0.28], [0.66, 0.68], [0.85, 0.6], [1, 0.55]],
      markers: [
        { index: 1, label: "IPO day pop" },
        { index: 5, label: "Lock-up expiry" }
      ]
    },
    howToTrade: [
      "Mark the lock-up expiry date the moment a company IPOs — it's public information from the prospectus",
      "Be cautious extrapolating long-term conviction from first-day trading with such a thin float",
      "Watch trading volume and price action specifically around the lock-up expiry window",
      "Read the actual prospectus for insider share counts rather than relying on headline enthusiasm"
    ],
    cheatSheet: {
      entry: "Mark the lock-up expiry date from day one and revisit closer to it",
      stopLoss: "Reading too much long-term signal into thin-float first-day trading",
      target: "Pairs well with market-cap-and-free-float for the supply picture",
      timeframes: "First trading day, then again near the 90-180 day lock-up expiry",
      bias: "Event-driven volatility around two known dates"
    },
    mistakes: [
      "Judging long-term prospects purely from first-day trading action",
      "Being surprised by a lock-up-expiry sell-off that was scheduled from day one",
      "Ignoring the quiet period and over-weighting sparse company commentary right after listing"
    ]
  },
  {
    slug: "sector-rotation",
    title: "Sector Rotation",
    category: "stock-fundamentals",
    track: "stocks",
    accent: "lime",
    bias: "neutral",
    tagline: "Money doesn't leave the market so much as it moves between sectors.",
    whatItIs:
      "Sector rotation describes how investor money shifts between industry groups (tech, financials, energy, healthcare, and others) as the economic cycle and interest-rate environment change. Different sectors tend to lead or lag at different stages — growth-heavy sectors often lead when conditions are easy, while defensive sectors often hold up better when conditions tighten.",
    howToSpot: [
      "Track relative performance of sector indices/ETFs against the broad market, not just their absolute price",
      "Rate-sensitive growth sectors often lead in low-rate, easy-liquidity environments",
      "Defensive sectors (utilities, staples, healthcare) often hold up better when growth expectations fall",
      "Energy and materials often track commodity cycles more than the broad market's mood",
      "A sector that's been the weakest for a while can become the next leader once conditions shift"
    ],
    psychology:
      "It's tempting to keep chasing whichever sector already led the last stretch, but rotation exists precisely because leadership changes as conditions change — the crowd piling into last cycle's winner is often buying right as the next rotation begins. Recognizing that money is cyclical between sectors, not just between 'in the market' and 'out of the market', is the core of the concept.",
    analogy: {
      title: "Passengers moving between train carriages",
      body:
        "Picture the whole market as a moving train and sectors as its carriages. Passengers (money) don't get off the train each time conditions change — they mostly just walk to a different carriage that suits the ride better right now. The train's total passenger count barely changes; where everyone is sitting shifts a lot."
    },
    diagram: {
      kind: "donut",
      segments: [
        { label: "Technology", value: 0.3, color: "violet" },
        { label: "Financials", value: 0.22, color: "cyan" },
        { label: "Energy", value: 0.18, color: "amber" },
        { label: "Healthcare", value: 0.16, color: "emerald" },
        { label: "Other sectors", value: 0.14, color: "slate" }
      ]
    },
    howToTrade: [
      "Track sector ETFs' relative strength against a broad index rather than watching sectors in isolation",
      "Pay attention to interest-rate and economic-cycle signals that historically precede rotations",
      "Avoid assuming last quarter's leading sector will automatically keep leading",
      "Diversify across sectors so a rotation away from your biggest weighting doesn't dominate your results"
    ],
    cheatSheet: {
      entry: "Watch relative sector strength against the broad index, not absolute price alone",
      stopLoss: "Chasing last cycle's leading sector right as conditions are already shifting",
      target: "Pairs well with market-cap-and-free-float for position sizing within a sector",
      timeframes: "Multi-month/quarterly view, tracked alongside the economic cycle",
      bias: "Portfolio-allocation concept, not a single-stock signal"
    },
    mistakes: [
      "Treating one sector's strength as a signal for the whole market's direction",
      "Ignoring how interest-rate changes tend to favor different sectors at different times",
      "Overconcentrating in whichever sector performed best recently, right before it can rotate out of favor"
    ]
  }

];

function tracksPayload() {
  return TRACKS.map((t) => ({
    id: t.id,
    label: t.label,
    count: PATTERNS.filter((p) => p.track === t.id).length
  }));
}

function categoriesPayload() {
  return CATEGORIES.map((c) => ({
    id: c.id,
    label: c.label,
    track: c.track,
    count: PATTERNS.filter((p) => p.category === c.id).length
  }));
}

function summaryPayload() {
  return PATTERNS.map((p) => ({
    slug: p.slug,
    title: p.title,
    category: p.category,
    track: p.track,
    accent: p.accent,
    bias: p.bias,
    tagline: p.tagline
  }));
}

function fullPayload() {
  return PATTERNS;
}

function findBySlug(slug) {
  return PATTERNS.find((x) => x.slug === slug) || null;
}

module.exports = { TRACKS, CATEGORIES, tracksPayload, categoriesPayload, summaryPayload, fullPayload, findBySlug };
