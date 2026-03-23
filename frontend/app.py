import streamlit as st
import requests
import pandas as pd

API_BASE = "http://localhost:8080/api/v1/companies"

st.set_page_config(page_title="Company Risk Assessment", layout="wide")
st.title("Company Risk Assessment")


def search_companies(company_name, registration_number, jurisdiction):
    params = {"jurisdiction": jurisdiction}
    if company_name:
        params["company_name"] = company_name
    if registration_number:
        params["registration_number"] = registration_number
    resp = requests.get(f"{API_BASE}/search", params=params, timeout=10)
    resp.raise_for_status()
    return resp.json()


def assess_company(company_number, company_name, jurisdiction):
    params = {
        "company_number": company_number,
        "company_name": company_name,
        "jurisdiction": jurisdiction,
    }
    resp = requests.get(f"{API_BASE}/assess", params=params, timeout=30)
    resp.raise_for_status()
    return resp.json()


def render_company_profile(profile):
    st.subheader("Company Profile")
    if not profile:
        st.warning("Company profile data unavailable")
        return
    df = pd.DataFrame([{
        "Name": profile.get("name", ""),
        "Registration Number": profile.get("number", ""),
        "Status": profile.get("status", "").capitalize(),
        "Incorporation Date": profile.get("incorporation_date", ""),
        "Date of Cessation": profile.get("date_of_cessation") or "—",
        "Registered Address": profile.get("registered_address", ""),
    }])
    st.dataframe(df, use_container_width=True, hide_index=True)


def render_officers(officers):
    st.subheader("Officers")
    if not officers:
        st.info("No officers data available")
        return
    rows = []
    for o in officers:
        rows.append({
            "Name": o.get("name", ""),
            "Role": o.get("role", "").capitalize(),
            "Appointed Date": o.get("appointed_date", ""),
            "Resigned Date": o.get("resigned_date") or "",
            "_sort_resigned": o.get("resigned_date") or "9999-12-31",
            "_sort_appointed": o.get("appointed_date") or "0000-01-01",
        })
    df = pd.DataFrame(rows)
    df = df.sort_values(["_sort_resigned", "_sort_appointed"], ascending=[False, False]).reset_index(drop=True)
    df = df.drop(columns=["_sort_resigned", "_sort_appointed"])
    df["Resigned Date"] = df["Resigned Date"].replace("", "Current")
    st.dataframe(df, use_container_width=True, hide_index=True)


def render_accounts(accounts):
    st.subheader("Annual Accounts Filings (Last 5 Years)")
    if not accounts:
        st.info("No accounts filings found")
        return
    rows = []
    for a in accounts:
        rows.append({
            "Filing Date": a.get("filing_date", ""),
            "Made Up Date": a.get("made_up_date", ""),
        })
    df = pd.DataFrame(rows)
    df = df.sort_values("Filing Date", ascending=False).reset_index(drop=True)
    st.dataframe(df, use_container_width=True, hide_index=True)


def render_confirmation_statements(statements):
    st.subheader("Confirmation Statements Filings (Last 5 Years)")
    if not statements:
        st.info("No confirmation statement filings found")
        return
    rows = []
    for s in statements:
        rows.append({
            "Filing Date": s.get("filing_date", ""),
            "Made Up Date": s.get("made_up_date", ""),
        })
    df = pd.DataFrame(rows)
    df = df.sort_values("Filing Date", ascending=False).reset_index(drop=True)
    st.dataframe(df, use_container_width=True, hide_index=True)


def render_liquidation(liquidation):
    st.subheader("Liquidation")
    if not liquidation:
        st.info("No liquidation data available")
        return
    has = liquidation.get("has_liquidation", False)
    if has:
        st.error("Liquidation filings found")
        filings = liquidation.get("filings", [])
        if filings:
            rows = []
            for f in filings:
                rows.append({
                    "Filing Date": f.get("filing_date", ""),
                    "Type": f.get("type", ""),
                    "Description": f.get("description", ""),
                })
            df = pd.DataFrame(rows)
            st.dataframe(df, use_container_width=True, hide_index=True)
    else:
        st.success("No liquidation filings")


def render_adverse_media(findings):
    st.subheader("Adverse Media")
    st.caption("Source: LLM knowledge search — not live web results. Data may be incomplete or outdated.")
    if not findings:
        st.success("No adverse media found")
        return
    for f in findings:
        with st.expander(f.get("headline", "Finding")):
            st.write(f"**Source:** {f.get('source', 'Unknown')}")
            if f.get("date"):
                st.write(f"**Date:** {f['date']}")
            st.write(f.get("summary", ""))


def render_confidence(confidence):
    st.subheader("Confidence")
    if not confidence:
        st.warning("Confidence data unavailable")
        return
    col1, col2 = st.columns(2)
    with col1:
        completeness = confidence.get("completeness_score", 0)
        st.metric("Completeness", f"{completeness:.0%}")
    with col2:
        coverage = confidence.get("source_coverage", 0)
        st.metric("Source Coverage", f"{coverage:.0%}")

    used = confidence.get("sources_used", [])
    failed = confidence.get("sources_failed", [])
    if used:
        st.write(f"**Sources used:** {', '.join(used)}")
    if failed:
        st.warning(f"**Sources failed:** {', '.join(failed)}")


def render_assessment(assessment):
    render_company_profile(assessment.get("company"))
    render_officers(assessment.get("officers"))
    render_accounts(assessment.get("accounts"))
    render_confirmation_statements(assessment.get("confirmation_statements"))
    render_liquidation(assessment.get("liquidation"))
    render_adverse_media(assessment.get("adverse_media"))
    render_confidence(assessment.get("confidence"))
    st.caption(f"Assessed at: {assessment.get('assessed_at', '')}")


# --- Session State Init ---
if "candidates" not in st.session_state:
    st.session_state.candidates = []
if "search_message" not in st.session_state:
    st.session_state.search_message = ""
if "assessment" not in st.session_state:
    st.session_state.assessment = None
if "jurisdiction" not in st.session_state:
    st.session_state.jurisdiction = "GB"

# --- Search Form ---
with st.form("search_form"):
    col1, col2, col3 = st.columns([2, 2, 1])
    with col1:
        company_name = st.text_input("Company Name", placeholder="e.g. Acme Consulting Ltd")
    with col2:
        registration_number = st.text_input("Registration Number", placeholder="e.g. 12345678")
    with col3:
        jurisdiction = st.selectbox("Jurisdiction", ["GB"])
    submitted = st.form_submit_button("Search", type="primary")

if submitted:
    st.session_state.candidates = []
    st.session_state.search_message = ""
    st.session_state.assessment = None
    st.session_state.jurisdiction = jurisdiction

    if not company_name and not registration_number:
        st.error("Enter a company name or registration number")
    else:
        with st.spinner("Searching..."):
            try:
                result = search_companies(company_name, registration_number, jurisdiction)
            except requests.exceptions.RequestException as e:
                st.error(f"Search failed: {e}")
                st.stop()

        candidates = result.get("candidates", [])
        message = result.get("message", "")
        st.session_state.candidates = candidates
        st.session_state.search_message = message

        if not candidates:
            st.warning(message)
        elif len(candidates) == 1:
            c = candidates[0]
            if c.get("name_mismatch"):
                st.warning(
                    f"Name mismatch: registration number resolved to "
                    f"**{c['name']}**, which differs from the provided name."
                )
            st.info(f"Found: **{c['name']}** ({c['number']}) — {c['status']}")
            with st.spinner("Gathering data and assessing..."):
                try:
                    assessment = assess_company(c["number"], c["name"], jurisdiction)
                    st.session_state.assessment = assessment
                except requests.exceptions.RequestException as e:
                    st.error(f"Assessment failed: {e}")
                    st.stop()

# --- Disambiguation (multiple matches, persisted in session) ---
if st.session_state.candidates and len(st.session_state.candidates) > 1 and st.session_state.assessment is None:
    st.info(st.session_state.search_message)
    for i, c in enumerate(st.session_state.candidates):
        cessation = f" | Ceased: {c['date_of_cessation']}" if c.get("date_of_cessation") else ""
        label = f"{c['name']} ({c['number']}) — {c['status']} | Inc: {c.get('incorporation_date', 'N/A')}{cessation}"
        if st.button(label, key=f"select_{i}"):
            with st.spinner("Gathering data and assessing..."):
                try:
                    assessment = assess_company(c["number"], c["name"], st.session_state.jurisdiction)
                    st.session_state.assessment = assessment
                    st.rerun()
                except requests.exceptions.RequestException as e:
                    st.error(f"Assessment failed: {e}")

# --- Render Assessment ---
if st.session_state.assessment:
    render_assessment(st.session_state.assessment)
