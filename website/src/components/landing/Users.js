import React from 'react';
import useBaseUrl from '@docusaurus/useBaseUrl';
import {JOIN_URL, USERS} from '../../lib/users.mjs';

function Logo({src, user, mode}) {
  return (
    <img
      src={useBaseUrl(src)}
      alt={user.name}
      width={user.width}
      height={user.height}
      loading="lazy"
      {...(user.logoScale ? {style: {'--lp-logo-scale': user.logoScale}} : {})}
      {...(mode ? {className: `lp-logo-${mode}`} : {})}
    />
  );
}

// A logo with a dark-mode variant renders both images; CSS shows the one matching the colour mode, so there is no
// flash before hydration. The hidden one is display: none, so screen readers announce the name once.
function User({user}) {
  return (
    <li className="lp-user">
      <a href={user.url}>
        {user.logoDark ? (
          <>
            <Logo src={user.logo} user={user} mode="light" />
            <Logo src={user.logoDark} user={user} mode="dark" />
          </>
        ) : (
          <Logo src={user.logo} user={user} />
        )}
        <span className="lp-desc">{user.description}</span>
      </a>
    </li>
  );
}

export default function Users() {
  return (
    <section id="users" className="lp-section" aria-labelledby="users-title">
      <div className="lp-wrap">
        <p className="lp-label"><span className="lp-num">04</span> Users</p>
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
