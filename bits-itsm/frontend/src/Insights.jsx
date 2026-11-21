import React, { useEffect, useState } from 'react';
import { ResponsiveContainer, LineChart, Line, BarChart, Bar, XAxis, YAxis, Tooltip, Legend, CartesianGrid } from 'recharts';
import { apiRequest } from './api.js';
import { readableLabel } from './MyIncidents.jsx';

// One accent hue for "volume", a muted partner for comparison, a warm hue reserved for risk.
const ACCENT = '#19536b';
const MUTED = '#9db7cb';
const RISK = '#9c3843';
const AXIS = { fontSize: 12, fill: '#59728a' };

function Section({ title, note, children }) {
  return <section className="dashboard-incidents admin-section insights-section" aria-label={title}>
    <div className="dashboard-section-heading"><h2>{title}</h2>{note && <span>{note}</span>}</div>
    <div className="insights-body">{children}</div>
  </section>;
}

function DataTable({ headers, rows, empty }) {
  if (!rows.length) return <p className="dashboard-empty">{empty}</p>;
  return <div className="dashboard-table-scroll" role="region" tabIndex={0}>
    <table><thead><tr>{headers.map(h => <th scope="col" key={h}>{h}</th>)}</tr></thead>
      <tbody>{rows.map((row, i) => <tr key={i}>{row.map((cell, j) => <td key={j}>{cell ?? '—'}</td>)}</tr>)}</tbody></table>
  </div>;
}

function toSeries(map, raw = false) { return Object.entries(map || {}).map(([name, value]) => ({ name: raw ? name : readableLabel(name), value })); }

const shortDay = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });

export default function Insights() {
  const [state, setState] = useState({ loading: true });
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    setState(previous => ({ ...previous, loading: true, error: '' }));
    Promise.all(['admin/insights/analytics', 'admin/insights/major-incidents', 'admin/insights/sla-risk', 'admin/sms-outbox', 'incidents/lifecycle'].map(path => apiRequest(path)))
      .then(([analytics, majors, risk, sms, lifecycle]) => { if (active) setState({ loading: false, analytics, majors, risk, sms, lifecycle }); })
      .catch(error => { if (active) setState({ loading: false, error: error.message }); });
    return () => { active = false; };
  }, [refresh]);

  const { loading, error, analytics: a, majors = [], risk = [], sms = [], lifecycle = [] } = state;
  const toolbar = <div className="report-actions insights-toolbar">
    <button className="report-clear" disabled={loading} onClick={() => setRefresh(v => v + 1)}>Refresh</button>
    <a className="incident-view" href="/api/admin/reports/incidents.csv" download>Export CSV</a>
    <button className="incident-view" type="button" onClick={() => window.print()}>Export PDF (print)</button>
  </div>;
  if (loading && !a) return <>{toolbar}<p role="status">Loading insights…</p></>;
  if (error) return <>{toolbar}<p className="report-error" role="alert">{error} <button className="report-clear" onClick={() => setRefresh(v => v + 1)}>Retry</button></p></>;

  const kpis = [['Total incidents', a.total], ['Open', a.open], ['Critical open (P1)', a.critical], ['Resolved', a.resolved], ['SLA violations', a.slaViolations],
    ['SLA compliance', a.slaCompliancePercent == null ? '—' : `${a.slaCompliancePercent}%`], ['MTTA', a.mttaMinutes == null ? '—' : `${a.mttaMinutes} min`],
    ['MTTR', a.mttrHours == null ? '—' : `${a.mttrHours} h`], ['Avg feedback', a.averageFeedback == null ? '—' : `${a.averageFeedback} / 5`]];
  const workload = a.workloads.map(w => ({ name: w.name.split(' ')[0], active: w.total, capacity: Math.max(0, w.maxWorkload - w.total) }));
  const trend = a.trend.map(d => ({ ...d, label: shortDay(d.day) }));

  return <div className="insights">
    {toolbar}
    <div className="dashboard-summary insights-kpis">{kpis.map(([label, value]) => <div className="dashboard-summary-card" key={label}><h3>{label}</h3><p>{value}</p></div>)}</div>

    <div className="insights-grid">
      <Section title="Incident trend (30 days)" note="Reported vs resolved per day">
        <ResponsiveContainer width="100%" height={240}>
          <LineChart data={trend} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
            <CartesianGrid stroke="#e6edf4" vertical={false} />
            <XAxis dataKey="label" tick={AXIS} interval={6} tickLine={false} axisLine={false} />
            <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
            <Tooltip /><Legend />
            <Line type="monotone" dataKey="reported" name="Reported" stroke={ACCENT} strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="resolved" name="Resolved" stroke={MUTED} strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </Section>
      <Section title="Incidents per service" note="Service analysis">
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={toSeries(a.byService, true)} layout="vertical" margin={{ top: 4, right: 16, left: 24, bottom: 0 }}>
            <XAxis type="number" allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
            <YAxis type="category" dataKey="name" width={130} tick={AXIS} tickLine={false} axisLine={false} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} />
            <Bar dataKey="value" name="Incidents" fill={ACCENT} radius={[0, 4, 4, 0]} barSize={14} />
          </BarChart>
        </ResponsiveContainer>
      </Section>
      <Section title="Technician workload" note="Active vs remaining capacity">
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={workload} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
            <CartesianGrid stroke="#e6edf4" vertical={false} />
            <XAxis dataKey="name" tick={AXIS} tickLine={false} axisLine={false} />
            <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} /><Legend />
            <Bar dataKey="active" name="Active" stackId="w" fill={ACCENT} barSize={22} />
            <Bar dataKey="capacity" name="Remaining capacity" stackId="w" fill={MUTED} radius={[4, 4, 0, 0]} barSize={22} />
          </BarChart>
        </ResponsiveContainer>
      </Section>
      <Section title="Category distribution">
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={toSeries(a.byCategory)} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
            <CartesianGrid stroke="#e6edf4" vertical={false} />
            <XAxis dataKey="name" tick={AXIS} tickLine={false} axisLine={false} interval={0} angle={-30} textAnchor="end" height={60} />
            <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} />
            <Bar dataKey="value" name="Incidents" fill={ACCENT} radius={[4, 4, 0, 0]} barSize={22} />
          </BarChart>
        </ResponsiveContainer>
      </Section>
    </div>

    <Section title="Major incidents (correlation)" note="≥3 reports on one service within 30 min">
      <DataTable empty="No correlated incident clusters right now." headers={['Anchor', 'Service', 'Reports', 'Incidents', 'Window']}
        rows={majors.map(m => [m.anchorLabel, m.serviceLabel, m.incidentIds.length, m.labels.join(', '), `${new Date(m.firstReported).toLocaleTimeString()} – ${new Date(m.lastReported).toLocaleTimeString()}`])} />
    </Section>

    <Section title="SLA risk prediction" note="Elapsed time · workload · category MTTR">
      <DataTable empty="No open prioritised incidents." headers={['Incident', 'Title', 'Priority', 'Risk', 'Score', 'Why']}
        rows={risk.slice(0, 12).map(r => [r.label, r.title, r.priority, <span className={`admin-badge badge-${r.level === 'HIGH' ? 'breached' : r.level === 'MEDIUM' ? 'at-risk' : 'met'}`}>{readableLabel(r.level)}</span>, r.score.toFixed(2), r.reasons.join(' · ')])} />
    </Section>

    <div className="insights-stack">
      <Section title="Simulated SMS outbox" note="Adapter → fake SMS vendor">
        <DataTable empty="No SMS sent yet. SMS is used for SLA breaches, escalations and major incidents." headers={['Time', 'To', 'Message', 'Delivered']}
          rows={sms.map(s => [new Date(s.at).toLocaleTimeString(), s.phone, s.text, s.delivered ? 'Yes' : <span style={{ color: RISK }}>Failed</span>])} />
      </Section>
      <Section title="Lifecycle (State pattern)" note="From the Java state classes">
        <DataTable empty="" headers={['State', 'Can move to (who)', 'SLA clock']}
          rows={lifecycle.map(s => [readableLabel(s.status), s.terminal ? 'Terminal' : s.next.map(n => `${readableLabel(n)} (${(s.actors[n] || []).map(readableLabel).join(', ')})`).join('; '), s.slaClockRunning ? 'Running' : 'Paused / stopped'])} />
      </Section>
    </div>
  </div>;
}
