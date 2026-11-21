import React, { useEffect, useState } from 'react';
import { apiRequest } from './api.js';
import { readableLabel } from './MyIncidents.jsx';

const firstName = (name) => (name || '').trim().split(/\s+/)[0] || 'there';
const greeting = () => { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; };

/** Role-aware welcome banner shown above the live incident list. */
export function Welcome({ user, subtitle, action }) {
  return <section className="role-welcome">
    <div><span className="role-welcome-eyebrow">{new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}</span>
      <h2>{greeting()}, {firstName(user?.name)}</h2><p>{subtitle}</p></div>
    {action}
  </section>;
}

function RiskList({ items, empty }) {
  if (!items.length) return <p className="dashboard-empty">{empty}</p>;
  return <ul className="risk-list">{items.map(r => <li key={r.incidentId}>
    <span className={`risk-dot risk-${r.level.toLowerCase()}`} aria-hidden="true" />
    <div><strong>{r.label} · {r.title}</strong><span>{r.reasons.slice(0, 2).join(' · ')}</span></div>
    <span className={`admin-badge badge-${r.level === 'HIGH' ? 'breached' : r.level === 'MEDIUM' ? 'at-risk' : 'met'}`}>{readableLabel(r.level)} · {r.priority}</span>
  </li>)}</ul>;
}

/** Technician home: own incidents ranked by predicted SLA risk. */
export function TechnicianFocus() {
  const [risk, setRisk] = useState(null);
  useEffect(() => { let a = true; apiRequest('technician/sla-risk').then(d => { if (a) setRisk(d); }).catch(() => { if (a) setRisk([]); }); return () => { a = false; }; }, []);
  if (!risk) return null;
  return <section className="dashboard-incidents admin-section home-panel" aria-label="Your SLA focus">
    <div className="dashboard-section-heading"><h2>Work on these first</h2><span>Predicted SLA risk</span></div>
    <div className="insights-body"><RiskList items={risk.filter(r => r.level !== 'LOW').slice(0, 4)} empty="Nothing at risk. All your open incidents are on track." /></div>
  </section>;
}

/** Administrator home: live KPIs, alerts and the riskiest incidents, then the full incident list below. */
export function AdminHome({ onNavigate }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let a = true;
    Promise.all([apiRequest('admin/insights/analytics'), apiRequest('admin/insights/sla-risk'), apiRequest('admin/insights/major-incidents')])
      .then(([analytics, risk, majors]) => { if (a) setData({ analytics, risk, majors }); }).catch(e => { if (a) setError(e.message); });
    return () => { a = false; };
  }, []);
  if (error) return <p className="report-error" role="alert">{error}</p>;
  if (!data) return <p role="status">Loading overview…</p>;
  const { analytics: k, risk, majors } = data;
  const cards = [
    ['Open incidents', k.open, ''], ['Critical (P1) open', k.critical, k.critical ? 'danger' : 'ok'], ['SLA violations', k.slaViolations, k.slaViolations ? 'danger' : 'ok'],
    ['SLA compliance', k.slaCompliancePercent == null ? '—' : `${k.slaCompliancePercent}%`, k.slaCompliancePercent == null ? '' : k.slaCompliancePercent >= 90 ? 'ok' : 'warn'],
    ['MTTR', k.mttrHours == null ? '—' : `${k.mttrHours} h`, ''], ['Avg feedback', k.averageFeedback == null ? '—' : `${k.averageFeedback}/5`, ''],
  ];
  return <>
    {majors.length > 0 && <div className="major-banner" role="alert"><strong>Major incident:</strong> {majors[0].size ?? majors[0].incidentIds.length} reports on {majors[0].serviceLabel} ({majors[0].labels.join(', ')}). <button className="link-button" onClick={() => onNavigate('Insights & Analytics')}>Open insights</button></div>}
    <div className="dashboard-summary kpi-row">{cards.map(([label, value, tone]) => <div className={`dashboard-summary-card${tone ? ` tone-${tone}` : ''}`} key={label}><h3>{label}</h3><p>{value}</p></div>)}</div>
    <section className="dashboard-incidents admin-section home-panel" aria-label="Incidents at risk">
      <div className="dashboard-section-heading"><h2>Needs attention</h2><button className="report-clear" onClick={() => onNavigate('Insights & Analytics')}>All insights →</button></div>
      <div className="insights-body"><RiskList items={risk.filter(r => r.level !== 'LOW').slice(0, 5)} empty="No incident is at risk of missing its SLA." /></div>
    </section>
  </>;
}
