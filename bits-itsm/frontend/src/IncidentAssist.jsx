import React, { useEffect, useState } from 'react';
import { apiRequest } from './api.js';

/** Intelligent feature D: top-3 technician recommendations with the reasons behind each score. */
export function Recommendations({ incident, onPick }) {
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    apiRequest(`incidents/${incident.id}/recommendations`).then(data => { if (active) setItems(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [incident.id, incident.status]);
  return <section className="student-detail-section"><h3>Smart technician recommendation</h3>
    <p>Score = 35% skill match + 25% free capacity + 20% experience + 20% past fixes on this service.</p>
    {error ? <p className="report-error">{error}</p> : !items ? <p role="status">Ranking technicians…</p> : items.length ? <ol className="assist-list">{items.map(r => <li key={r.technicianId}>
      <strong>{r.name}</strong> · score {r.score.toFixed(2)} · {r.workload}/{r.maxWorkload} active
      <div className="assist-reasons">{r.reasons.join(' · ')}</div>
      {onPick && <button type="button" className="report-clear" onClick={() => onPick(String(r.technicianId))}>Select</button>}
    </li>)}</ol> : <p>No eligible technician is available.</p>}
  </section>;
}

/** Intelligent feature G: how the three most similar past incidents were resolved (TF-IDF similarity). */
export function SuggestedResolutions({ incident, role }) {
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    apiRequest(`${role === 'ADMIN' ? 'admin' : 'technician'}/incidents/${incident.id}/suggested-resolutions`).then(data => { if (active) setItems(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [incident.id, role]);
  return <section className="student-detail-section"><h3>Suggested resolutions</h3>
    {error ? <p className="report-error">{error}</p> : !items ? <p role="status">Searching past resolutions…</p> : items.length ? <ol className="assist-list">{items.map(s => <li key={s.incidentId}>
      <strong>INC-{s.incidentId}: {s.title}</strong> · {Math.round(s.similarity * 100)}% similar
      <div>{s.summary}</div>{s.steps && <div className="assist-reasons">Steps: {s.steps}</div>}
    </li>)}</ol> : <p>No sufficiently similar resolved incident yet.</p>}
  </section>;
}
