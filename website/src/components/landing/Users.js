import React from 'react';
import useBaseUrl from '@docusaurus/useBaseUrl';
import {JOIN_URL, USERS} from '../../lib/users.mjs';

function User({user}) {
  return (
    <li className="lp-user">
      <a href={user.url}>
        <img src={useBaseUrl(user.logo)} alt={user.name} width="380" height="70" loading="lazy" />
        <span className="lp-desc">{user.description}</span>
      </a>
    </li>
  );
}

export default function Users() {
  return (
    <section id="users" className="lp-section" aria-labelledby="users-title">
      <div className="lp-wrap">
        <p className="lp-label">04 &nbsp; Users</p>
        <h2 id="users-title">Who Is Using ZIO Blocks?</h2>
        <p className="lp-sub">Organizations and projects that run ZIO Blocks in production.</p>
        <ul className="lp-users">
          {USERS.map((u) => <User key={u.name} user={u} />)}
        </ul>
        <p className="lp-join">
          Are you using ZIO Blocks? <a href={JOIN_URL}>Let us know and join the list!</a>
        </p>
      </div>
    </section>
  );
}
