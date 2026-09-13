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

const CATEGORIES = [
  { id: "chart", label: "Chart Patterns" },
  { id: "candlestick", label: "Candlestick Patterns" },
  { id: "smc", label: "Smart Money Concepts" }
];

const PATTERNS = [
  // ---------------------------------------------------------------- CHART
  {
    slug: "head-and-shoulders",
    title: "Head and Shoulders",
    category: "chart",
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
  }
];

function categoriesPayload() {
  return CATEGORIES.map((c) => ({
    id: c.id,
    label: c.label,
    count: PATTERNS.filter((p) => p.category === c.id).length
  }));
}

function summaryPayload() {
  return PATTERNS.map((p) => ({
    slug: p.slug,
    title: p.title,
    category: p.category,
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

module.exports = { CATEGORIES, categoriesPayload, summaryPayload, fullPayload, findBySlug };
