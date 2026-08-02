import { connection } from "next/server";
import { AuthStatus } from "@/components/auth/auth-status";

const publicProposalSteps = [
  "학생이 제안을 등록합니다.",
  "다른 학생들이 제안을 읽고 동의합니다.",
  "50명 이상이 동의하면 정식 안건이 됩니다.",
  "학교가 검토 상태와 공식 답변을 남깁니다.",
];

export default async function Home() {
  await connection();

  return (
    <>
      <header className="site-header">
        <a className="brand" href="#main">
          학교 소통 제안 시스템
        </a>
        <nav aria-label="주요 메뉴">
          <a href="#process">진행 방식</a>
          <a href="#paths">작성 경로</a>
          <AuthStatus />
        </nav>
      </header>

      <main id="main">
        <section className="intro" aria-labelledby="intro-title">
          <p className="eyebrow">학생자치 기반 소통</p>
          <h1 id="intro-title">학생의 의견이 학교의 답변으로 이어지도록</h1>
          <p className="lede">
            학교생활을 더 낫게 만들 제안을 함께 살펴보고, 공감하는 제안이 정식 안건이 되도록
            동의할 수 있습니다.
          </p>
          <a className="text-action" href="#paths">
            제안 경로 알아보기 <span aria-hidden="true">→</span>
          </a>
        </section>

        <section id="process" className="section" aria-labelledby="process-title">
          <div className="section-heading">
            <p className="section-index">01</p>
            <h2 id="process-title">공개 제안은 이렇게 진행됩니다</h2>
          </div>
          <ol className="steps">
            {publicProposalSteps.map((step, index) => (
              <li key={step}>
                <span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
                <p>{step}</p>
              </li>
            ))}
          </ol>
        </section>

        <section id="paths" className="section" aria-labelledby="paths-title">
          <div className="section-heading">
            <p className="section-index">02</p>
            <h2 id="paths-title">내용에 맞는 경로를 선택합니다</h2>
          </div>
          <div className="paths">
            <article>
              <p className="path-label">공개</p>
              <h3>학교 개선 제안</h3>
              <p>
                수업, 기숙사, 시설, 학생자치, 정보 공개처럼 학생들이 함께 논의할 내용을
                제안합니다. 학생들에게 공개되고 동의를 받습니다.
              </p>
            </article>
            <article>
              <p className="path-label">비공개</p>
              <h3>개인 고충·안전 제보</h3>
              <p>
                학교폭력, 자해 위험, 개인정보, 성희롱, 차별처럼 공개 투표로 다루면 안 되는
                내용은 지정된 담당자에게 별도로 전달합니다.
              </p>
            </article>
          </div>
        </section>
      </main>

      <footer>
        <p>서비스 정식 명칭과 자체 로고·브랜드 컬러는 추후 확정됩니다.</p>
      </footer>
    </>
  );
}
