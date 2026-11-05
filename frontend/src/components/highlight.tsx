import { Fragment } from "react"

interface HeadlineProps {
  text: string
  highlight: string
}

/** Title with the keyword highlighted (only one per title). */
export function Headline({ text, highlight }: HeadlineProps) {
  const index = highlight ? text.indexOf(highlight) : -1
  if (index === -1) {
    return <>{text}</>
  }

  return (
    <Fragment>
      {text.slice(0, index)}
      <span className="rounded-sm bg-sky-wash px-2 py-0.5 text-cyan-edge">{highlight}</span>
      {text.slice(index + highlight.length)}
    </Fragment>
  )
}
