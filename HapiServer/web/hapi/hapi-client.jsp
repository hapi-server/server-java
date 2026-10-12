<%@page import="org.hapiserver.HapiServerSupport"%>
<%@page import="org.codehaus.jettison.json.JSONObject"%>
<%@page import="org.hapiserver.Initialize"%>
<%@page import="org.hapiserver.Util"%>
<%
        String HAPI_HOME= Initialize.getHapiHome(getServletContext());           
        
        String uri = request.getRequestURI();

        boolean withinHapi= uri.endsWith("/hapi/"); // if true, then references must be relative
        String h="hapi/";
        if ( withinHapi ) h="";
        
        JSONObject landingConfig= HapiServerSupport.getLandingConfig(HAPI_HOME);
        JSONObject about= HapiServerSupport.getAbout(HAPI_HOME);
%>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title><%= Util.escapeHtml(about.optString("title","Basic HAPI Server")) %></title>
<%
            if ( landingConfig.has("style") ) {
                String styleUrl= landingConfig.getString("style");
                out.println("<link rel=\"stylesheet\" href=\""+styleUrl+"\">");
            }
%>
<style>
body{font:15px system-ui,sans-serif;max-width:850px;margin:2rem auto;padding:0 1rem;color:#222}input,button{font:inherit;padding:.45rem}input[type=search]{width:100%;box-sizing:border-box}fieldset{border:1px solid #ccc;margin:1rem 0}#datasets{max-height:220px;overflow:auto;border:1px solid #ddd}#datasets button{display:block;width:100%;text-align:left;border:0;background:white;cursor:pointer;overflow-wrap:anywhere}#datasets button:hover,#datasets button:focus{background:#eaf2ff}.param{display:block;margin:.4rem 0}.dates{display:flex;gap:1rem;flex-wrap:wrap}.dates label{flex:1;min-width:230px}.dates input{width:100%;box-sizing:border-box}#url{overflow-wrap:anywhere;font-family:monospace;font-size:.85rem}#status{min-height:1.5em;color:#555}button{cursor:pointer}button:disabled{cursor:not-allowed;opacity:.55}
</style>
</head>
<body>
<h1><%= Util.escapeHtml(about.optString("title","Basic HAPI Server")) %></h1>  
<%
                String defaultDescription="More information about this type of server is found at "
                    + "<a href=\"https://github.com/hapi-server/server-java\" target=\"_blank\">GitHub</a>."
        + " This implementation of the HAPI server uses plug-in readers to load data.  Discussion and more about this "
        + " server can be found <a href=\"https://github.com/hapi-server/server-java/blob/main/README.md\">here</a>.";
%>
        <%= Util.escapeHtml(about.optString("description",defaultDescription)) %> 
 <h3>Some example requests:</h3>
        <a href="<%=h%>about">About</a> <i>More about this server, like contact info.</i><br>
        <a href="<%=h%>capabilities">Capabilities</a> <i>Capabilities of the server.</i><br>
        <a href="<%=h%>catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        
        <br>
        
<label>Dataset search <input id="search" type="search" placeholder="Search dataset IDs and descriptions" autocomplete="off"></label>
<p id="count"></p><div id="datasets" role="listbox" aria-label="Datasets"></div>
<div class="selection-heading"><h2 id="selected">Select a dataset</h2><a id="info-link" href="#" target="_blank" rel="noopener noreferrer" hidden>info &nearr;</a></div>
<fieldset><legend>Parameters</legend><div id="params">Choose a dataset first.</div></fieldset>
<div class="dates"><label>Start (ISO 8601)<input id="start" placeholder="2020-01-01T00:00:00Z"></label><label>Stop (ISO 8601)<input id="stop" placeholder="2020-01-02T00:00:00Z"></label></div>
<p><button id="download" disabled>Download CSV</button> <button id="copy" disabled>Copy URL</button></p>
<p id="url"></p><p id="status" role="status"></p>
<script>
'use strict';
(() => {
  // Same-origin by default. To use another HAPI server, set this to its /hapi URL.
  const BASE = new URL('./', location.href).href.replace(/\/$/, '');
  const $ = id => document.getElementById(id);
  let catalog = [], selectedId = null, info = null, loadVersion = 0;
  const status = message => { $('status').textContent = message; };
  const endpoint = (name, values = {}) => {
    const u = new URL(BASE + '/' + name);
    Object.entries(values).forEach(([key, value]) => u.searchParams.set(key, value));
    return u;
  };
  async function json(url) {
    const response = await fetch(url, {headers:{Accept:'application/json'}});
    if (!response.ok) throw new Error('HTTP ' + response.status + ' from ' + url.pathname);
    const result = await response.json();
    if (result.status && result.status.code >= 1400) throw new Error(result.status.message || 'HAPI error');
    return result;
  }
  function renderCatalog() {
    const q = $('search').value.trim().toLowerCase();
    const matches = catalog.filter(d => (d.id + ' ' + (d.title || '')).toLowerCase().includes(q));
    $('count').textContent = `${matches.length} matching datasets (${catalog.length} total)`;
    const box = $('datasets'); box.replaceChildren();
    // Render a bounded set; filtering still searches the entire catalog.
    for (const d of matches.slice(0, 4000)) {
      const b = document.createElement('button');
      b.type = 'button'; b.textContent = d.id + (d.title ? ' \u2014 ' + d.title : '');
      b.addEventListener('click', () => selectDataset(d.id)); box.append(b);
    }
    if (matches.length > 4000) {
      const p = document.createElement('p'); p.textContent = 'Showing first 4000 matches. Refine your search.'; box.append(p);
    }
  }
  async function selectDataset(id) {
    selectedId = id; info = null; const version = ++loadVersion;
    $('selected').textContent = id;
    $('info-link').href = endpoint('info', {id}).href;
    $('info-link').hidden = false;
    $('params').textContent = 'Loading parameters\u2026';
    updateURL(); status('');
    try {
      const result = await json(endpoint('info', {id}));
      if (version !== loadVersion) return;
      info = result;
      const box = $('params'); box.replaceChildren();
      (result.parameters || []).forEach((p, index) => {
        const label = document.createElement('label'); label.className = 'param';
        const cb = document.createElement('input'); cb.type = 'checkbox'; cb.value = p.name;
        cb.checked = true; cb.disabled = index === 0; // HAPI's first parameter is Time.
        cb.addEventListener('change', updateURL);
        label.append(cb, document.createTextNode(' ' + p.name + (p.description ? ' \u2014 ' + p.description : '')));
        box.append(label);
      });
      $('start').value = result.sampleStartDate || result.startDate || '';
      $('stop').value = result.sampleStopDate || result.stopDate || '';
      updateURL();
    } catch (e) { if (version === loadVersion) { $('params').textContent = 'Unable to load parameters.'; status(e.message); } }
  }
  function requestURL() {
    if (!info || !selectedId) return null;
    const start = $('start').value.trim(), stop = $('stop').value.trim();
    const selected = [...$('params').querySelectorAll('input:checked')].map(x => x.value);
    if (!start || !stop || !selected.length) return null;
    return endpoint('data', {id:selectedId, parameters:selected.join(','), start, stop, format:'csv'}).href;
  }
  function updateURL() {
    const url = requestURL(); $('url').textContent = url || 'Select a dataset and enter a start and stop time.';
    $('download').disabled = $('copy').disabled = !url;
  }
  $('search').addEventListener('input', renderCatalog);
  $('start').addEventListener('input', updateURL);
  $('stop').addEventListener('input', updateURL);
  $('download').addEventListener('click', () => {
    const url = requestURL(); if (!url) return;
    // Browser handles the response stream, not JavaScript. Same-origin server
    // should set Content-Disposition: attachment for a reliable download.
    const a = document.createElement('a'); a.href = url;
    a.download = (selectedId || 'hapi').replace(/[^a-z0-9._-]/gi, '_') + '.csv';
    document.body.append(a); a.click(); a.remove();
  });
  $('copy').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(requestURL()); status('URL copied.'); }
    catch (e) { status('Copy failed: ' + e.message); }
  });
  (async () => {
    try { status('Loading catalog\u2026'); const result = await json(endpoint('catalog'));
      catalog = result.catalog || []; renderCatalog(); status('');
    } catch (e) { status('Could not load catalog: ' + e.message); }
  })();
})();
</script>
</body>
</html>
