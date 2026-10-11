/**
 * Action: alerts.create_ticket
 * Builds a plain-text ticket body that can be pasted into Jira, stores it as a
 * file under data/action-output/alerts/ and returns it for the copy dialog.
 *
 * Runs inside the GraalJS sandbox. Only `ctx` is available (see docs/01-schema-grammar.md §8).
 *
 * @param {ActionContext} ctx
 * @returns {ActionResult}
 */
function createTicket(ctx) {
  const a = ctx.record;
  const d = ctx.display;
  const severity = d.alert_type || a.alert_type;
  const summary = "[" + severity + "] " + a.alert_name;

  const lines = [
    "Summary: " + summary,
    "",
    "h3. Alert details",
    "||Field||Value||",
    "|Alert ID|" + a.id + "|",
    "|Date (UTC)|" + a.alert_date + "|",
    "|Type|" + severity + "|",
    "|Group|" + (d.alert_group || a.alert_group) + "|",
    "|Source|" + orDash(a.alert_source) + "|",
    "|Site|" + orDash(a.alert_site) + "|",
    "",
    "h3. Description",
    orDash(a.alert_description),
    "",
    "----",
    "Raised by " + ctx.user.username + " at " + ctx.now() + " via Templated Data Service."
  ];
  const content = lines.join("\n");

  const file = ctx.files.writeText("ticket-alert-" + a.id + ".txt", content);
  ctx.log.info("Ticket text generated for alert " + a.id);

  return { kind: "text", title: summary, content: content, file: file };
}

function orDash(v) {
  return v === null || v === undefined || String(v).trim() === "" ? "-" : String(v);
}
