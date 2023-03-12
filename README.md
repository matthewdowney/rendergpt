# rendergpt

A Chrome extension to render structured output from ChatGPT into rich text and other views.

**Status**: alpha/experimental.

Currently, the extension just adds a "render" button to any HTML code blocks in 
the conversation. 

Clicking the button renders the HTML in an iframe, and allows selecting other
code blocks (JavaScript, CSS) from the conversation for inclusion, mixing and 
matching sources.

![rendering some HTML, CSS, and JavaScript](rendergpt.gif)

## Rationale

ChatGPT does pretty well with structured output. I've had success getting it to
build diagrams with PlantUML syntax, draw SVGs of varying complexity, and of 
course compose HTMl, CSS, and JavaScript.

It'd be cool to be able to render these outputs and still preserve the 
interactive workflow (it's kind of a REPL, isn't it?). 

I also wonder how well it could do a first pass of a less structured 
[draw.io](draw.io) diagram or similar.

## Ideas 

- [x] Render HTML/CSS/JavaScript in an iframe inside the conversation
- [x] Allow mixing and matching different code blocks from the conversation
- [ ] Render PlantUML diagrams. I've been copy-and-pasting ChatGPT output into
  [plantuml.com's web app](http://www.plantuml.com/plantuml/uml/SyfFKj2rKt3CoKnELR1Io4ZDoSa70000)
  and honestly it does a decent job. Certainly useful as a first pass.
- [ ] Sometimes it takes a long time for it to re-stream an entire code block 
  when you've only asked it to make a small modification. It'd be cool if you
  could **instruct it to respond with a git patch**, and then have the extension 
  apply the patch for you.
- [ ] Render ClojureScript / Reagent components as well (perhaps via Scittle?)
- [ ] Render plots. Ofc this can be done via JS, but depending on its ability to
  fetch tabular data, it might be nice to be able to toggle a plot view.

## Usage

**First**, build the extension in development mode with the dev Babashka task:

    $ bb dev

Or equivalently, via npm, `npx shadow-cljs watch :app`.

**Second**, load the public/ directory as an unpacked extension in Chrome/Brave.

**Finally**, start a ChatGPT conversation. The "render" button only appears 
for code blocks tagged "html" for now. 

For some reason, ChatGPT sometimes tags HTML as "php" and requires a gentle 
reminder that we no longer use that technology by choice.
